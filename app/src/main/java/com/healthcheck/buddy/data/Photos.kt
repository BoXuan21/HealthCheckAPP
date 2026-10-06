package com.healthcheck.buddy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stores photos in app-private storage and prepares them for the vision API. */
object Photos {
    private const val MAX_EDGE = 1568 // Claude's recommended max image edge

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    /** A fresh file + content:// Uri for the camera to write into. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Copies a picked image into app storage, downscaled and rotated upright. Returns the saved file. */
    fun importAndNormalize(context: Context, source: Uri): File {
        val target = File(dir(context), "${UUID.randomUUID()}.jpg")
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        writeNormalized(bytes, target)
        return target
    }

    /** Normalizes a camera capture in place. */
    fun normalizeInPlace(file: File) {
        writeNormalized(file.readBytes(), file)
    }

    private fun writeNormalized(bytes: ByteArray, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })

        val rotation = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                postRotate(rotation)
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}
