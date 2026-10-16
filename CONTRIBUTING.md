# Contributing to LawnchairZ

Use [this repository's issues](https://github.com/corrifa1/LawnchairZ/issues) for LawnchairZ bugs and feature requests. Include the LawnchairZ version, Android version, device model, reproduction steps, and expected behavior. Share only screenshots or logs you intend to make public.

LawnchairZ is a fork of [Expressive Launcher](https://github.com/denson9874/ExpressiveLauncher). Report problems that also affect Expressive Launcher to that project as well.

Start with the [build guide](docs/BUILDING.md) and [source provenance](docs/SOURCE_PROVENANCE.md). Keep changes focused, preserve the Launcher3/Lawnchair architecture and existing source notices, and include meaningful regression coverage for functional changes. Explain how a pull request was validated, including device checks when behavior is visible on Home.

Contributor builds use their own development or signing configuration. Archived upstream GitHub workflows in [docs/reference/upstream-github](docs/reference/upstream-github/) are retained for context; they do not run from this repository.

## Preserve contribution credit

- Retain applicable source headers, component licenses, and the root [NOTICE](NOTICE),
  including the Expressive Launcher NOTICE it reproduces. Preserve original authors when
  importing commits; identify the upstream repository, license, and exact source revision
  for copied or adapted material.
- Add a prominent modification notice when changing an inherited file, keeping existing
  notices. In Expressive-origin files, add
  `Modified by <name> for LawnchairZ: https://github.com/corrifa1/LawnchairZ` below the
  Expressive lines; never edit Expressive's copyright line, URL, or `Expressive modification:`
  comments.
- For a new Apache-licensed LawnchairZ source file, include an accurate copyright notice
  (for example `Copyright 2026 Corey Flynn and LawnchairZ contributors.`),
  `SPDX-License-Identifier: Apache-2.0`, and the project URL.
  A contributor retains ownership of their own copyrightable contributions.
- Explain the origin of a contribution and credit coauthors accurately. Update the
  [source provenance](docs/SOURCE_PROVENANCE.md) or
  [third-party notices](docs/THIRD_PARTY_NOTICES.md) when introducing a new origin or component.
- Keep `android/lawnchair/assets/NOTICE.txt` identical to the root NOTICE. Run
  `python3 ci/check_attribution.py` before submitting. License-text changes require
  deliberate review of the relevant rights and the check's recorded hashes.

GitHub Actions checks attribution, runs app unit tests and developer/benchmark
builds, verifies merged launcher manifest security, and performs CodeQL analysis.
See [reuse and attribution](docs/REUSE_AND_ATTRIBUTION.md) for downstream guidance.
