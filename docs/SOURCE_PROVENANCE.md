# Source provenance

LawnchairZ is maintained by Corey Flynn ([corrifa1](https://github.com/corrifa1)) at
[corrifa1/LawnchairZ](https://github.com/corrifa1/LawnchairZ). It is a fork of
[Expressive Launcher](https://github.com/denson9874/ExpressiveLauncher) by Daryl Denson and
Expressive Launcher contributors. Expressive Launcher in turn builds on
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair) and
[AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3).

## LawnchairZ fork point

| Item | Identity |
| --- | --- |
| Upstream repository | [denson9874/ExpressiveLauncher](https://github.com/denson9874/ExpressiveLauncher) |
| Fork point | [`73b6592add1b3c572b1cf1eb481d8d8285e664c7`](https://github.com/denson9874/ExpressiveLauncher/commit/73b6592add1b3c572b1cf1eb481d8d8285e664c7) (Expressive `main`, 2026-09-30, "Merge pull request #33") |
| LawnchairZ application ID | `dev.launcher.lawnchairz.l3` (Expressive uses `dev.launcher.expressive.l3`) |
| LawnchairZ Discover helper ID | `dev.launcher.lawnchairz.feed` (Expressive uses `dev.launcher.expressive.feed`) |
| LawnchairZ releases | None published yet. Releases, tags, signed APKs, checksums and update manifests named in the Expressive record below belong to the Expressive Launcher repository. |

The full LawnchairZ history starts after the fork point, so
`git log 73b6592add..` lists every LawnchairZ change and its author, and
`git diff --stat 73b6592add` lists every file LawnchairZ added or changed.

## LawnchairZ contribution index

These paths do not exist at the Expressive fork point. They carry the
`Copyright 2026 Corey Flynn and LawnchairZ contributors.` notice.

| Contribution | Source |
| --- | --- |
| Widget stacks | [widgetstack/](../android/lawnchair/src/app/lawnchair/widgetstack/), [widget-stacks.md](widget-stacks.md) |
| Fold bridge and hinge detection | [fold/](../android/lawnchair/src/app/lawnchair/fold/) |
| Launcher-side return-home animation and animation speed | [AppReturnAnimator.kt](../android/lawnchair/src/app/lawnchair/animation/AppReturnAnimator.kt), [AppReturnPolicy.kt](../android/lawnchair/src/app/lawnchair/animation/AppReturnPolicy.kt), [AppReturnTracker.kt](../android/lawnchair/src/app/lawnchair/animation/AppReturnTracker.kt), [LauncherAnimationSpeed.kt](../android/lawnchair/src/app/lawnchair/animation/LauncherAnimationSpeed.kt) |
| Side dock search button | [SideDockSearchButton.kt](../android/lawnchair/src/app/lawnchair/qsb/SideDockSearchButton.kt) |

LawnchairZ also changes inherited AOSP, Lawnchair and Expressive files, for example for
the side dock, dock and grid handling on foldables, and widget-stack support in the
launcher model. Those files keep their original notices, including Expressive's
`Expressive modification:` comments.

## Expressive Launcher provenance record

The record below is Expressive Launcher's `docs/SOURCE_PROVENANCE.md` at the fork point,
retained unchanged apart from heading levels. In it, "this public repository", tags,
releases and the `main` branch refer to
[denson9874/ExpressiveLauncher](https://github.com/denson9874/ExpressiveLauncher).


This public repository is based on the application source snapshot for **Expressive Launcher 1.0.12 / version code 13**. The current `main` branch also includes the **Expressive Bloom** branding update described below; the original snapshot is preserved by `source-v1.0.12-13`.

| Item | Identity |
| --- | --- |
| Application source revision used for the signed build | `ad7a5c336423df6edb198a700a050117808c96f6` |
| Lawnchair 16 baseline | [`eed2baf4efe4cf49540cf4ec474942dc743b83cc`](https://github.com/LawnchairLauncher/lawnchair/tree/eed2baf4efe4cf49540cf4ec474942dc743b83cc) |
| Required SystemUI submodule | [`e12acf0978875fc4adcebf46207b766106ffc92b`](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui/tree/e12acf0978875fc4adcebf46207b766106ffc92b) |
| Signed QA release | [`qa-v1.0.12-13`](https://github.com/denson9874/ExpressiveLauncher/releases/tag/qa-v1.0.12-13) |
| Public source tag | `source-v1.0.12-13` |
| APK SHA-256 | `778bb1720151c4b486ddc5d400fb0b69bc2c725cd692f2d246cf17dc6ebf88a1` |

### Publication snapshot

The application was developed in a separate working repository. Its source revision above identifies the build's recorded input; that local commit history was not imported into this repository. The initial public commit, `1ba2ee49b0f165dd1f2e67cf68323a51a13006ef`, and the `source-v1.0.12-13` tag contain the source snapshot with the publication documentation described below. The existing public repository history and original release assets are preserved.

The application implementation, resources, tests, build dependencies, Gradle files and wrapper were retained from the recorded source. Git normalized the Google Fonts catalog from CRLF to LF line endings; its JSON data is unchanged. Publication preparation adds the README, comparison graphics, build/download guides and readable third-party notices. It replaces workstation-specific paths in the Jenkins environment blocks and selected operations documents with `/path/to/...` placeholders. Configure those paths for your own environment before using the CI templates.

Inherited GitHub workflow and community configuration is retained under [`docs/reference/upstream-github`](reference/upstream-github/). It is reference material; it is not enabled as Expressive automation. The repository-root contribution guide now points to Expressive. Original license notices and source headers remain intact. No private signing material, developer local.properties or retained personal-device evidence is included.

The later [attribution workflow](../.github/workflows/attribution.yml) is an
Expressive-specific notice check. It does not enable the archived upstream
workflows or build, sign, or publish application releases.

### Expressive contribution index

The following are selected Expressive additions in the public source snapshot.
Their paths do not exist in the recorded Lawnchair baseline
`eed2baf4efe4cf49540cf4ec474942dc743b83cc`; their implementations are present in the
original public source snapshot. This is a contribution index, not a claim that
the surrounding packages, dependencies, or launcher features originated here.

| Contribution | Source |
| --- | --- |
| Expressive update-manifest validation and channel decisions | [ExpressiveUpdatePolicy.kt](../android/lawnchair/src/app/lawnchair/ui/preferences/about/ExpressiveUpdatePolicy.kt) |
| Update notification scheduling, reminders, and snooze decisions | [ExpressiveUpdateNotifications.kt](../android/lawnchair/src/app/lawnchair/ui/preferences/about/ExpressiveUpdateNotifications.kt) |
| Notification permission and opt-in controls for Expressive updates | [ExpressiveUpdateNotificationControl.kt](../android/lawnchair/src/app/lawnchair/ui/preferences/about/ExpressiveUpdateNotificationControl.kt) |
| Discover helper inspection and verification before requesting Android installation | [ExpressiveFeedSetup.kt](../android/lawnchair/src/app/lawnchair/feed/ExpressiveFeedSetup.kt) |

The attribution hardening adds notices to those files without changing their
implementations. The original [source snapshot](https://github.com/denson9874/ExpressiveLauncher/tree/source-v1.0.12-13)
and [upstream baseline](https://github.com/LawnchairLauncher/lawnchair/tree/eed2baf4efe4cf49540cf4ec474942dc743b83cc)
remain available for comparison. The [feature comparison](FEATURE_COMPARISON.md)
credits the inherited functionality. [NOTICE](../NOTICE) and the
[reuse guide](REUSE_AND_ATTRIBUTION.md) describe attribution for later distributions
that include these notices; earlier published copies retain their existing terms.

### Expressive Bloom branding update

The `main` branch now carries the approved [Expressive Bloom artwork](assets/expressive/expressive-bloom.png), coordinated repository graphics, and updated Android and store icon resources. Builds made from this source use the new branding. The application version remains **1.0.12 / version code 13** in this public source baseline.

This branding change does not replace the historical signed APK identified above. Its source tag, package identity, checksum, validation results, and release downloads remain associated with the original build and icon. Each newly published QA or stable build supplies its own version and verification evidence through the existing release process.

### Get the complete source

```sh
git clone --recurse-submodules --branch source-v1.0.12-13 https://github.com/denson9874/ExpressiveLauncher.git
```

GitHub's normal source ZIP contains the parent repository but omits submodule contents. Recursive cloning retrieves the pinned SystemUI code required by `settings.gradle`. See [BUILDING.md](BUILDING.md).

The older `qa-v1.0.11-12` and `qa-v1.0.12-13` release tags were created while this repository held exports only. Their automatically generated source ZIPs still reflect that history. Use the new source tag or `main` for application code; download the APK and evidence from the release's named assets.

### Foundation and scope

Expressive inherits Launcher3 through Lawnchair's Android 16-derived baseline and targets Android 17 / API37 for its current app build. The separately staged full Android 17 Launcher3 core port is not complete. [ANDROID17_PORT.md](ANDROID17_PORT.md) records that distinction and upstream reference points.
