# Identify SDK V3 — Sample Integration

This repository is a **reference integration**, not the SDK itself. It shows exactly how a
host app is expected to call the Identify SDK's public API — config setup, initialization,
starting a verification flow, module selection, hooks — so integrators can copy real,
working call patterns instead of guessing.

The SDK's implementation (`sdk-core`, `sdk-ui-default`, `sdk-ui-colendi`) is **not** included
here. It's consumed as a compiled Maven dependency via GitHub Packages, exactly as any
partner app would consume it.

---

## What's in here

| Path | What it shows |
|---|---|
| `app/src/main/kotlin/com/identify/sample/MainViewModel.kt` | The full call sequence: `SdkConfig.Builder(...)`, `IdentifySdk.init(...)`, `IdentifySdk.startAuthentication(...)`, module selection, SSL pinning, NFC dependency injection |
| `app/src/main/kotlin/com/identify/sample/MainActivity.kt` | Minimal Activity hosting the SDK's Compose UI |
| `app/src/main/kotlin/com/identify/sample/ui/MainScreen.kt` | Example settings/config screen (server switch, module toggles, language) |
| `docs/` | Integration guides: SDK config reference, UI customization, hooks, OCR, guidance messages |

---

## 1. Prerequisites

You'll need a GitHub Personal Access Token with `read:packages` scope, and the runtime
secret values (turn server key, secret keys) — **provided to you by the Identify
integration team through a separate secure channel.** None of these are stored in this
repository.

## 2. Configure your credentials

```bash
cp local.properties.example local.properties
```

Edit `local.properties` and fill in the values you received:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_YOUR_PERSONAL_ACCESS_TOKEN

identify.turnKey=...
identify.secretKeyBase64=...
identify.loggerSecretKey=...
identify.socketSecretKey=...
```

`local.properties` is gitignored — it never leaves your machine. For CI, set the equivalent
environment variables instead: `GPR_USER`, `GPR_TOKEN`, `IDENTIFY_TURN_KEY`,
`IDENTIFY_SECRET_KEY_BASE64`, `IDENTIFY_LOGGER_SECRET_KEY`, `IDENTIFY_SOCKET_SECRET_KEY`
(env vars take priority over `local.properties`, see `settings.gradle.kts` / `app/build.gradle.kts`).

## 3. Build and run

```bash
./gradlew :app:installDebug
```

---

## Where the actual service calls happen

Everything a real integration needs is in `MainViewModel.startProcess(...)`:

```kotlin
val builder = SdkConfig.Builder(baseUrl, BuildConfig.IDENTIFY_TURN_KEY)
    .setIntroEnabled(true)
    .setUiProvider(StandardUiProvider())
    .setCustomModules(manualModules)
    .setLanguage(currentLanguage)
    .setApiTimeout(120)
    .setSecretKeyBase64(BuildConfig.IDENTIFY_SECRET_KEY_BASE64)
    .setLoggerSecretKey(BuildConfig.IDENTIFY_LOGGER_SECRET_KEY)
    .setSocketSecretKey(BuildConfig.IDENTIFY_SOCKET_SECRET_KEY)
    .setSslPins(sslPinsForCurrentBaseUrl())

val config = builder.build()
IdentifySdk.init(application = activity.application, config = config)
IdentifySdk.startAuthentication(activity, identIdValue)
```

Read `docs/sdk-config.md` for every available `SdkConfig.Builder` option, and `docs/hooks.md`
for lifecycle callbacks (`onIdentifyFinished`, `onIdentifyFailed`, `onIdentifyCancelled`).

## Support

For questions about the SDK itself, GitHub Packages access, or environment-specific
credentials, contact the Identify integration team.
