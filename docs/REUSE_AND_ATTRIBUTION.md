# Reusing LawnchairZ with attribution

LawnchairZ is maintained by Corey Flynn ([corrifa1](https://github.com/corrifa1)) and its
contributors at [corrifa1/LawnchairZ](https://github.com/corrifa1/LawnchairZ).
It is a fork of [Expressive Launcher](https://github.com/denson9874/ExpressiveLauncher) by
Daryl Denson and Expressive Launcher contributors, which builds on Lawnchair and AOSP
Launcher3. The [source provenance](SOURCE_PROVENANCE.md) records the fork point and
separates LawnchairZ contributions from Expressive's and from their upstream foundation.

## What redistribution requires

The existing [Apache License 2.0](../LICENSE.txt) continues to apply to code covered
by that license. Component-specific licenses remain in force; consult
[third-party notices](THIRD_PARTY_NOTICES.md) and the files you actually reuse.
This guide and [NOTICE](../NOTICE) do not introduce a new license.

For Apache-licensed work, section 4 requires redistributors to:

1. Give recipients a copy of the Apache license.
2. Put prominent notices in modified files stating that they were changed.
3. Retain relevant copyright, patent, trademark, and attribution notices in
   distributed derivative source, excluding notices unrelated to the parts reused.
4. Carry forward the relevant attribution from an included `NOTICE` in a readable
   location allowed by section 4(d): a distributed NOTICE file, accompanying
   source or documentation, or the usual third-party-notice display.

The LawnchairZ [NOTICE](../NOTICE) contains Expressive Launcher's NOTICE unchanged.
Carry both parts forward when you redistribute work that includes them.

## How LawnchairZ keeps upstream credit

- Files from Expressive Launcher keep `Copyright 2026 Daryl Denson and Expressive Launcher
  contributors.`, their `Developed for Expressive Launcher` line and Expressive's URL.
  When LawnchairZ changes one, it adds a `Modified by Corey Flynn for LawnchairZ` line.
- Expressive's `Expressive modification:` comments in AOSP and Lawnchair files stay as
  written. LawnchairZ marks its own later changes separately.
- AOSP and Lawnchair copyright headers and the license texts are never changed.
- New LawnchairZ files carry `Copyright 2026 Corey Flynn and LawnchairZ contributors.`,
  `SPDX-License-Identifier: Apache-2.0` and the LawnchairZ URL.

## A practical acknowledgment

When incorporating LawnchairZ contributions, this acknowledgment makes their origin
easy to find. It supplements the applicable license and notices; it does not replace them:

```text
Includes work from LawnchairZ by Corey Flynn and contributors.
https://github.com/corrifa1/LawnchairZ
Based on Expressive Launcher, AOSP Launcher3 and Lawnchair; see the included licenses and notices.
```

Work that originated in Expressive Launcher should also credit that project, as its
[reuse guide](https://github.com/denson9874/ExpressiveLauncher/blob/main/docs/REUSE_AND_ATTRIBUTION.md)
describes.

For source reuse, retain existing headers, record the source commit or release,
and describe your changes. Preserving commit authors when cherry-picking and
linking to the original contribution are useful ways to keep a traceable record.
The project's contribution workflow is described in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Project identity

The LawnchairZ name identifies this project. It is independent and is not affiliated
with or endorsed by the Expressive Launcher, Lawnchair, or Android Open Source Project
maintainers. Apache section 6 does not grant trademark permission beyond customary
identification of a work's origin and reproduction of its notices.

The Expressive Bloom artwork identifies Expressive Launcher. It remains in this
repository only until LawnchairZ's own icon replaces it, and LawnchairZ does not present
itself as an Expressive release.

## What the repository protections do

The attribution check verifies the root and bundled notices (including the unchanged
Expressive NOTICE), the Expressive notices in selected Expressive-origin source files,
and the retained license texts. Run it with:

```sh
python3 ci/check_attribution.py
python3 -m unittest discover -s ci/tests -p 'test_check_attribution.py'
```

The GitHub workflow runs this check for changes to `main`. The maintainer is listed
in [CODEOWNERS](../.github/CODEOWNERS) for notices, provenance, and the guard itself.
CODEOWNERS alone does not enforce approval. These controls protect this repository's
record and catch accidental omissions. They cannot control a separately hosted copy.

The copy at `android/lawnchair/assets/expressive-NOTICE.txt` uses the existing Lawnchair
asset directory so source builds carry the NOTICE with the app. It must stay identical to
the root NOTICE.

## If attribution appears to be missing

Record the other repository or download URL, its exact commit or version, the
matching LawnchairZ files and revisions, and the notices supplied with that copy.
Check accompanying source, documentation, and third-party notices before concluding
that attribution was omitted. Contact the maintainer through the
[project repository](https://github.com/corrifa1/LawnchairZ) with that evidence; avoid
posting personal information or unsupported accusations. Questions about Expressive
Launcher's own contributions belong with
[its maintainer](https://github.com/denson9874/ExpressiveLauncher).

Public code remains downloadable and reusable under its applicable licenses.
Existing Apache grants cannot simply be withdrawn, and this NOTICE does not
retroactively change earlier snapshots.

## Authoritative references

- [Apache License 2.0, especially sections 2, 4, and 6](https://www.apache.org/licenses/LICENSE-2.0)
- [Apache guidance on applying notices and SPDX headers](https://www.apache.org/foundation/license-faq#Apply-My-Software)
- [GitHub guidance on licensing public repositories](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
