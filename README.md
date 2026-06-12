# ZenoAi Android App

A production-ready native Android application that hosts [ZenoAi](https://zenoai-bot.vercel.app/) inside a secure, high-performance WebView — built for NGAI.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM (ViewModel + LiveData) |
| UI | Material Design 3, ViewBinding |
| WebView | AndroidX WebKit |
| Push Notifications | Firebase Cloud Messaging |
| Analytics | Firebase Analytics |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 35 |

---

## Project Structure

```
ZenoAi/
├── app/
│   ├── src/main/
│   │   ├── java/com/ngai/zenoai/
│   │   │   ├── ZenoAiApplication.kt          # App class, notification channels
│   │   │   ├── ui/
│   │   │   │   ├── splash/SplashActivity.kt  # Branded splash screen
│   │   │   │   └── main/
│   │   │   │       ├── MainActivity.kt       # Core activity
│   │   │   │       ├── ZenoWebViewClient.kt  # Navigation, error handling
│   │   │   │       ├── ZenoWebChromeClient.kt# Permissions, file upload
│   │   │   │       └── ZenoJsInterface.kt    # JS ↔ Native bridge
│   │   │   ├── viewmodel/MainViewModel.kt    # State management
│   │   │   ├── service/ZenoFcmService.kt     # FCM push notifications
│   │   │   ├── receiver/
│   │   │   │   ├── DownloadReceiver.kt       # Download complete
│   │   │   │   └── NetworkReceiver.kt        # Network changes
│   │   │   └── utils/
│   │   │       ├── Constants.kt
│   │   │       ├── NetworkUtils.kt + NetworkLiveData
│   │   │       ├── PreferenceManager.kt
│   │   │       ├── HapticUtils.kt
│   │   │       └── ExtensionFunctions.kt
│   │   └── res/
│   │       ├── layout/                       # All XML layouts
│   │       ├── drawable/                     # Vector icons & backgrounds
│   │       ├── values/ + values-night/       # Themes, colors, strings (dark mode)
│   │       ├── anim/                         # Transition animations
│   │       ├── font/                         # Inter font family
│   │       └── xml/                          # Network security, file provider, backup
│   ├── google-services.json                  # ← REPLACE with yours
│   └── proguard-rules.pro
├── gradle/libs.versions.toml                 # Version catalog
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Setup Instructions

### 1 — Prerequisites

- **Android Studio Ladybug** (2024.2) or newer
- **JDK 17**
- **Android SDK** with API 35 installed

### 2 — Firebase Configuration (Required)

The app will **not compile** without a valid `google-services.json`.

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Create (or select) your project
3. Add an Android app:
   - **Package name:** `com.ngai.zenoai`
   - **App nickname:** ZenoAi
4. Download `google-services.json`
5. Replace `app/google-services.json` with the downloaded file
6. Enable **Cloud Messaging** in Firebase Console → Project Settings → Cloud Messaging

### 3 — Fonts (Required for build)

Download [Inter](https://fonts.google.com/specimen/Inter) and add to `app/src/main/res/font/`:

| File | Weight |
|---|---|
| `inter_regular.ttf` | 400 |
| `inter_medium.ttf` | 500 |
| `inter_bold.ttf` | 700 |

**Android Studio shortcut:** Right-click `res` → New → Android Resource Directory → `font`. Then Right-click `font` → New → Font resource file → search "Inter" (uses Downloadable Fonts — no TTF needed).

### 4 — Build & Run

```bash
# Clone / open the project in Android Studio
# Let Gradle sync complete

# Debug build
./gradlew assembleDebug

# Release build (requires signing config — see below)
./gradlew assembleRelease
```

### 5 — Signing for Play Store

Create `keystore.properties` in the project root (never commit this):

```properties
storeFile=your-release-key.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then add to `app/build.gradle` under `android {}`:

```groovy
signingConfigs {
    release {
        def props = new Properties()
        props.load(new FileInputStream(rootProject.file("keystore.properties")))
        storeFile file(props['storeFile'])
        storePassword props['storePassword']
        keyAlias props['keyAlias']
        keyPassword props['keyPassword']
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
        // ... existing config
    }
}
```

---

## Features

### WebView
- JavaScript, DOM storage, localStorage, cookies, third-party cookies — all enabled
- Session and auth persistence across app restarts
- Hardware-accelerated rendering
- Pull-to-refresh
- File upload (gallery + camera capture)
- Camera / microphone / geolocation permission handling
- Native download manager integration
- Custom JS bridge (`window.ZenoAndroid`) for native share, haptics, FCM token

### Navigation
- WebView back stack navigation
- Exit confirmation dialog on home page
- Last visited URL restored on restart
- Deep link support (`https://zenoai-bot.vercel.app/...`)
- FCM notification tap opens app at correct URL

### Offline & Error Handling
- Real-time network monitoring via `NetworkLiveData`
- Snackbar feedback on connect/disconnect
- 5 custom error screens:
  - No Internet — with "Load Cached" option
  - Server Error
  - SSL Error — with security warning chip
  - Page Not Found
  - General Error
- Each screen has a Retry button

### Notifications (FCM)
- Android 13+ permission request with rationale
- 2 notification channels: General, Alerts
- Custom notification icon
- Deep-link via `data.url` in FCM payload
- FCM token exposed to web app via JS bridge

### Security
- HTTPS-only (`network_security_config.xml`)
- SSL errors hard-cancelled (no bypass)
- Malicious URL schemes blocked (`javascript:`, `vbscript:`, `file://`)
- WebView debugging disabled in release builds
- `allowFileAccess = false`, `allowContentAccess = false`
- `mixedContentMode = NEVER_ALLOW`

### UI/UX
- Material Design 3 throughout
- Dark mode support (full `values-night` color overrides)
- Edge-to-edge window with transparent status/nav bars
- Animated splash screen (AndroidX SplashScreen API)
- Haptic feedback via `HapticUtils`
- Native share sheet, native file picker

---

## FCM Payload Format

```json
{
  "to": "FCM_TOKEN",
  "notification": {
    "title": "New from ZenoAi",
    "body": "Your assistant has a response ready."
  },
  "data": {
    "url": "https://zenoai-bot.vercel.app/chat",
    "type": "general"
  }
}
```

`type` values: `general` (default channel) | `alert` or `urgent` (high-priority channel)

---

## JavaScript Bridge

The web app can call native Android features:

```javascript
// Check if running in native app
if (window.ZenoAndroid && window.ZenoAndroid.isNativeApp()) {
    // Native-only code

    // Haptic feedback
    window.ZenoAndroid.hapticFeedback('light');  // light | medium | heavy

    // Native share sheet
    window.ZenoAndroid.shareContent('Check out ZenoAi!', 'ZenoAi');

    // Get FCM token (to register with your backend)
    const token = window.ZenoAndroid.getFcmToken();

    // App version
    const version = window.ZenoAndroid.getAppVersion();
}
```

---

## Play Store Checklist

- [ ] Replace `google-services.json` with production Firebase config
- [ ] Add Inter font TTF files to `res/font/`
- [ ] Replace `ic_zeno_logo.xml` with final brand logo (or PNG assets)
- [ ] Add PNG launcher icons to all `mipmap-*` densities (use [Android Asset Studio](https://romannurik.github.io/AndroidAssets/))
- [ ] Create release keystore and configure signing
- [ ] Run `./gradlew assembleRelease`
- [ ] Test on physical device (Android 8 through Android 15)
- [ ] Upload AAB: `./gradlew bundleRelease`
- [ ] Complete Play Store listing (screenshots, description, privacy policy URL)

---

## License

© 2024 NGAI. All rights reserved.
