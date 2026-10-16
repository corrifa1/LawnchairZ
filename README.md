<p align="center">
  <img src="docs/assets/lawnchairz/lawnchairz-icon.svg" width="160" height="160" alt="LawnchairZ icon: a striped folding lawn chair on the screen of an open foldable phone" />
</p>

# LawnchairZ

**An Android 17 Home app tuned for foldables.** LawnchairZ builds on
[Expressive Launcher](https://github.com/denson9874/ExpressiveLauncher), which builds on
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair) and
[AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3).
It keeps their Home screens, app drawer, folders, widgets and customization, and adds
features for phones that fold.

LawnchairZ is maintained by Corey Flynn. It has **no published releases yet**; build it
from source as described below.

## What LawnchairZ adds

- **Widget stacks.** Stack several widgets in one spot and swipe between them, then edit,
  resize or unstack them from the stack's menu. See [widget stacks](docs/widget-stacks.md).
- **Side dock.** An optional iPhone Duo-style dock along the side of the unfolded screen,
  with adjustable icon spacing and a search button below the dock icons.
- **Fold bridge.** Folding and unfolding keeps both screens' layouts and stops widgets from
  reloading, including when the hinge sensor jumps straight to closed.
- **Return-home animation.** Apps animate back to their icon on devices whose system does
  not provide that animation, with an adjustable animation speed.

## What it inherits

- From **Expressive Launcher**: the Pixel-inspired settings dashboard with an Advanced
  group, the date-first At a Glance fallback with weather recovery, guided Google Discover
  setup with a bundled helper, and clearer search contact actions.
- From **Lawnchair**: icon packs, icon shapes, fonts and theming, global search including
  settings, Smartspacer support, and Nova Launcher backup import.
- From **Launcher3**: the launcher foundation itself.

The [feature comparison](docs/FEATURE_COMPARISON.md) and
[source provenance](docs/SOURCE_PROVENANCE.md) credit each part to its origin.

## Build from source

You need **JDK 21**, the **Android 37.1 SDK**, and an **Android 17 (API 37)** device or
emulator. The SystemUI submodule is required; GitHub's ZIP download omits it.

```sh
git clone --recurse-submodules https://github.com/corrifa1/LawnchairZ.git
cd LawnchairZ
./gradlew assembleLawnWithQuickstepLawnchairzDebug
./gradlew testLawnWithQuickstepLawnchairzDebugUnitTest
```

The debug APK installs as `dev.launcher.lawnchairz.l3.debug`. Android updates an installed
launcher in place, keeping your Home layout and settings, only when the application ID and
signing key match and the version code does not go down. Never uninstall your daily
launcher to switch builds. The [build guide](docs/BUILDING.md) covers setup, tests and
signing; the [Android source guide](android/README.md) maps the `android/` folders.

To report a problem, use [this repository's issues](https://github.com/corrifa1/LawnchairZ/issues).
Contributions start with [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits and license

LawnchairZ contributions are by Corey Flynn and LawnchairZ contributors. LawnchairZ is
based on Expressive Launcher by Daryl Denson and Expressive Launcher contributors, and on
the work of the Lawnchair and AOSP Launcher3 contributors.

The [Apache License 2.0](LICENSE.txt) applies alongside each component's own notices. The
[NOTICE](NOTICE) reproduces Expressive Launcher's NOTICE unchanged; see also the
[reuse and attribution guide](docs/REUSE_AND_ATTRIBUTION.md) and
[third-party notices](docs/THIRD_PARTY_NOTICES.md).

LawnchairZ is an independent project. It is not affiliated with or endorsed by the
Expressive Launcher, Lawnchair, Android Open Source Project, Google or Pixel teams.
