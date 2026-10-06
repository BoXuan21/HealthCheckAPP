package com.healthcheck.buddy

import android.app.Application
import com.healthcheck.buddy.data.Repository

class HealthCheckApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
    }
}
