# Build & test - Root (Android)

## One-time toolchain setup

This machine had no Android toolchain. Install these (JDK 17 because Gradle does
not support Java 24):

```bash
brew install openjdk@17
brew install gradle
brew install --cask android-commandlinetools
```

Then install the SDK packages and accept licences:

```bash
# Point tools at an SDK location
export ANDROID_HOME="$HOME/Library/Android/sdk"
mkdir -p "$ANDROID_HOME"
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

SDKMANAGER="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses
```

Create `local.properties` in the repo root so Gradle finds the SDK (and, optionally,
your free Groq key). This file is gitignored.

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
# Optional - without it the app runs in offline demo mode:
GROQ_API_KEY=gsk_your_free_groq_key
```

Get a free Groq key at https://console.groq.com (no card required).

## Generate the Gradle wrapper (first time only)

```bash
gradle wrapper --gradle-version 8.7
```

After this, always use `./gradlew` so builds are reproducible.

## Run the unit tests (this is our rigorous gate)

```bash
./gradlew testDebugUnitTest
```

Covers: time-of-day mapping, prompt building, the Groq client (via MockWebServer),
and the reflection ViewModel state machine.

## Build a debug APK and install on a device/emulator

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Lint

```bash
./gradlew lint
```

## Notes
- The hard native features (app-blocking, geofencing) are NOT in this slice; they
  need real-device verification (emulator or BrowserStack App Automate) when built.
- Java 24 remains the system default; only Gradle needs JAVA_HOME -> JDK 17.
