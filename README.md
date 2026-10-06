# HealthCheck

An Android health buddy, not a tracking app. Snap your food, log your weight, and chat with a buddy (named Kai by default) who knows your goals and your day.

## Features

| Tab | What it does | Needs API key |
|---|---|---|
| **Today** | Calorie ring + macros, meal log, a daily check-in from your buddy | Photo → calories does |
| **Buddy** | Chat with your buddy. It sees your profile, today's meals, your week and your weight trend | Yes |
| **Progress** | Weigh-ins with a trend chart, progress photos, and an honest "am I actually progressing?" check | The AI check does |
| **Plan** | Baseline targets (Mifflin–St Jeor × activity), plus an AI-optimized training/rest-day plan with sample meals | The AI plan does |

Without a key you still get tracking and baseline targets. Each AI feature shows an "Unlock" card until you add one.

## AI setup

1. Create a key at [console.anthropic.com](https://console.anthropic.com) → API Keys.
2. Paste it during onboarding or under **Settings → Claude API key**.

The key is encrypted with the Android Keystore and only sent to Anthropic. The app uses `claude-opus-5-5` through the official [Anthropic Java SDK](https://github.com/anthropics/anthropic-java). Food and progress analysis use structured JSON outputs. Requests also turn on server-side refusal fallback.

## Build

You need Android Studio (or the Android SDK with JDK 17+).

```sh
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # with a phone connected (USB debugging on)
```

Or open the folder in Android Studio and press Run.

## Code map

```
app/src/main/java/com/healthcheck/buddy/
  ai/ClaudeClient.kt     SDK wrapper: text + structured JSON calls, image blocks, error mapping
  ai/Buddy.kt            Persona prompt, food / progress / plan / chat / check-in features, JSON schemas
  domain/Nutrition.kt    Profile, BMR/TDEE, baseline macro targets
  data/                  Room DB (meals, weights, photos, chat), DataStore settings, photo handling
  ui/screens/            Compose screens
```
