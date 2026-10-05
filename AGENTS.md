# Repository Guidelines

## Project Structure & Module Organization

This repository currently contains the Android implementation specification in `DroidLottery_GUIA_IMPLEMENTACAO_ANDROID.md`; no Android/Gradle project has been committed. Use that guide when bootstrapping the app. Its intended layout is:

```text
droidlottery/
├── app/                 # Android application and UI entry point
├── core/                # models, configuration, security, logging
├── mining/              # controller, foreground service, stats, benchmarks
├── stratum/             # Stratum client, protocol, parsing, reconnect logic
├── thermal/             # device thermal/battery safeguards
├── native/              # NDK C/C++ mining engines, grouped by algorithm
└── ui/                  # screens and presentation components
```

Keep behavior in its owning module. Put JNI-facing code in `native/` and add tests in `src/test` or `src/androidTest`.

## Build, Test, and Development Commands

Once the Gradle project exists, use the wrapper rather than a machine-wide Gradle installation:

```sh
./gradlew assembleDebug       # build a debug APK
./gradlew test                # run JVM unit tests
./gradlew connectedAndroidTest # run instrumentation tests on a device/emulator
./gradlew lint                # run Android lint checks
```

Run native builds through Gradle/CMake. Do not commit generated APKs, build output, or local SDK settings.

## Coding Style & Naming Conventions

Use Kotlin by default, four-space indentation, null-safety, coroutines, and immutable `val` properties. Use `PascalCase` for types and `camelCase` for functions, properties, and packages. Match specification names, e.g. `MiningMode.LOTTERY_SOLO` and `MiningProfile`.

Use Java only for interop. Follow Android C++ conventions in `native/`, keep JNI names explicit, and avoid business logic in UI. Run a formatter/linter (for example, ktlint) when introduced.

## Testing Guidelines

Cover profile validation, Stratum parsing, reconnects, share results, and thermal stop/pause behavior. Name tests after observable behavior, such as `stopsMining_whenThermalLimitIsExceeded`. Tests must not submit real shares or expose credentials.

## Commit & Pull Request Guidelines

There is no Git history in this checkout; use concise imperative commits such as `feat: add Stratum V1 parser` or `fix: pause on critical thermal state`. Keep commits focused. Pull requests should explain behavior, testing, and security impact; attach UI screenshots and link issues when applicable.

## Security & Configuration

Never commit pool passwords, wallet secrets, keystores, or tokens. Use Android Keystore or ignored local configuration. Validate endpoints, prefer TLS when supported, and retain foreground-service and thermal safeguards.
