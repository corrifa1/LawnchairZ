# Build LawnchairZ

This guide covers building LawnchairZ from source. LawnchairZ has no published
releases yet, so a local build is how you get it. Its default version is still the
one inherited from Expressive Launcher (**1.0.12 / version code 13**); see
[source provenance](SOURCE_PROVENANCE.md).

## Requirements

| Dependency | Required configuration |
| --- | --- |
| Java | JDK 21; point `JAVA_HOME` or Android Studio's Gradle JDK to it |
| Android SDK | Compile platform API 37, minor API level 1 (Android SDK 37.1), plus Android SDK Build-Tools and Platform-Tools |
| Gradle | Use the included wrapper, pinned to **9.7.0**; a separate Gradle installation is unnecessary |
| Device or emulator | **Android 17 / API 37 or newer**; the app's minimum and target SDK are both 37 |
| Git | Submodule support and access to GitHub |
| Network | Needed to obtain the wrapper, Android components and Maven dependencies on the first build |

The build inputs are declared in [build.gradle](../android/build.gradle),
[the version catalog](../gradle/libs.versions.toml), and
[the wrapper configuration](../gradle/wrapper/gradle-wrapper.properties).
Install the requested SDK components through Android Studio's SDK Manager. Configure
the SDK with an ignored `local.properties` file containing `sdk.dir=...`, or with
your normal Android SDK environment configuration.

Open the **repository root** in Android Studio and run `./gradlew` there. Android
source and module build files live under `android/`; the root `settings.gradle`
keeps the existing Gradle project names and selects `android/build.gradle` as the
launcher build script. Keep `local.properties` and `keystore.properties` at the
repository root. APKs and test reports still go to the root `build/` directory.
See the [Android source guide](../android/README.md) for the folder map.

## Get the source and its submodule

```sh
git clone --recurse-submodules https://github.com/corrifa1/LawnchairZ.git
cd LawnchairZ
```

For an existing clone, or after changing revisions:

```sh
git submodule sync --recursive
git submodule update --init --recursive
git submodule status --recursive
```

The required submodule is `android/platform_frameworks_libs_systemui`, from
[LawnchairLauncher/platform_frameworks_libs_systemui](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui).
For this source snapshot, its recorded revision is
`e12acf0978875fc4adcebf46207b766106ffc92b`. The checked-in gitlink is authoritative;
use `git submodule update` to restore that revision rather than updating to the
latest upstream branch. Its URL is recorded in [.gitmodules](../.gitmodules).

## Build a developer APK

Confirm the Java runtime, then use the project wrapper:

```sh
java -version
./gradlew --version
./gradlew assembleLawnWithQuickstepLawnchairzDebug
```

The developer APK is written to:

```text
build/outputs/apk/lawnWithQuickstepLawnchairz/debug/*.apk
```

On Windows, run the equivalent commands with `gradlew.bat`.
The task name contains `WithQuickstep` because Lawnchair shares those implementation
classes. LawnchairZ is a standard Home app: Android SystemUI
continues to provide system gesture navigation and Recents. Root and QuickSwitch
are not required.

The default developer application ID is `dev.launcher.lawnchairz.l3.debug`.
QA builds use that same ID with your release certificate, so **a debug-signed APK and a
QA APK cannot update each other**. Android only updates an installed launcher in place,
keeping its Home layout and settings, when the application ID and signer match and the
version code does not go down. Never uninstall your daily launcher to switch builds.

## Run the app tests

Run the complete LawnchairZ unit-test suite:

```sh
./gradlew testLawnWithQuickstepLawnchairzDebugUnitTest
```

For example, run only the At a Glance setup and widget-binding regression tests:

```sh
./gradlew testLawnWithQuickstepLawnchairzDebugUnitTest \
  --tests app.lawnchair.smartspace.provider.SmartspaceSetupCoordinatorTest \
  --tests app.lawnchair.HeadlessWidgetBindingTest
```

Results are written to
`build/reports/tests/testLawnWithQuickstepLawnchairzDebugUnitTest/` and
`build/test-results/testLawnWithQuickstepLawnchairzDebugUnitTest/`.
The suite includes Android-resource/Robolectric tests under
[tests/lawnchairzUnit](../android/tests/lawnchairzUnit). Unit-test success does not replace
installation, upgrade and visible feature testing on an Android device or emulator.

## Build with your own signing identity

The `Qa` and `Release` variants require a complete local signing configuration;
they do not fall back to Android's debug certificate.

```sh
cp keystore.properties.example keystore.properties
```

Edit the ignored `keystore.properties` with your own keystore path, alias and
passwords, following [the example](../keystore.properties.example). The supported
keys are `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The build can
also read a properties file selected by `LAWNCHAIRZ_KEYSTORE_PROPERTIES`, or the
`LAWNCHAIRZ_RELEASE_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS` and `_KEY_PASSWORD`
environment variables. The older `EXPRESSIVE_*` names still work.
Signing keys and credentials are not included in this repository and must remain
outside commits and exports.

With your configuration present:

```sh
./gradlew assembleLawnWithQuickstepLawnchairzQa
```

The result is a minified, non-debuggable APK under
`build/outputs/apk/lawnWithQuickstepLawnchairz/qa/`. Forks should configure their own
application identity and signing lineage with the `lawnchairz*` Gradle properties in
[build.gradle](../android/build.gradle) (for example `-PlawnchairzApplicationId=...`).

## Port status

LawnchairZ runs on Android 17 while retaining its Lawnchair-derived Launcher3
foundation. The separate full AOSP Android 17 core port is **not complete**.
[The port notes](ANDROID17_PORT.md) record its provenance and remaining gates.
