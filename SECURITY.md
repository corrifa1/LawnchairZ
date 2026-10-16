# Security Policy

## Reporting a vulnerability

Please report suspected security vulnerabilities in LawnchairZ privately through
[GitHub's Report a vulnerability form](https://github.com/corrifa1/LawnchairZ/security/advisories/new).
You can also find it under **Security and quality → Advisories → Report a vulnerability**.

**Do not post vulnerability details, exploit code, credentials, or private user data in public issues, pull requests, or discussions.**
Use [ordinary issues](https://github.com/corrifa1/LawnchairZ/issues) for bugs and feature requests without a security impact.

Include as much of the following as you can:

- A clear description, the potential impact, and any prerequisites for exploitation.
- The LawnchairZ version name and version code, build type (debug, QA or release), and APK source. For source findings, include the commit and affected paths.
- Android version/build, device model, and relevant permissions or settings.
- Minimal reproduction steps or a safe proof of concept, with expected and observed behavior.
- Redacted logs or screenshots, and any suggested mitigation.

A complete exploit is not required to report a credible concern. Never include live signing keys, access tokens, passwords, or other people's data. If the private form is unavailable, open an issue asking only for a private reporting channel, without disclosing the vulnerability.

## Supported versions

LawnchairZ has no published releases yet. Security fixes target the `main` branch.
Forks and independently signed builds are maintained by their distributors.
Issues that also affect Expressive Launcher should be reported to
[that project](https://github.com/denson9874/ExpressiveLauncher) as well.

Do not uninstall or clear launcher data merely to reproduce a report.

See [source provenance](docs/SOURCE_PROVENANCE.md), identify the exact source revision or APK you tested, and do not assume a source finding has been verified in every release.

## Response and coordinated disclosure

Reports are handled by the LawnchairZ maintainer on a best-effort basis.
There is no guaranteed response or fix deadline.

The maintainer will use the private report to acknowledge receipt, request any needed information, and communicate assessment and progress. Confirmed issues are prioritized according to impact, exploitability, and exposure. If a report is declined or cannot be reproduced, the response should explain why and what additional evidence would help.

Please coordinate public disclosure with the maintainer so users can receive a fix or mitigation.
Any disclosure date and researcher credit should be agreed in the private report; credit is optional.

## System and security boundaries

LawnchairZ is an Android Home application built on Launcher3 and Lawnchair.
This policy covers LawnchairZ's app behavior, its bundled Discover feed helper, updater, and repository build and release tooling. Relevant areas include `android/lawnchair/`, `android/src/`, `android/quickstep/`, `android/expressive/`, `android/expressiveFeed/`, `ci/`, and `scripts/`.

Android provides the app sandbox, permissions, profile authentication, and package installation.
Other installed apps, exported-component callers, imported files, content providers, widgets, integrations, and network responses may supply untrusted input.

Security review should assess these requirements; this list is not a claim that every control has been independently audited:

- **User data and permissions:** launcher settings, contacts, files, search results, and other accessible data must not be exposed to unauthorized callers. Permission denial or revocation must be respected, including optional all-files access used by Blur wallpaper in applicable direct-distribution builds.
- **Profile privacy:** Private Space and work-profile behavior must respect Android's profile state and authentication boundaries. Recovery or search flows must not reveal protected contents or bypass required authentication.
- **Component access:** exported activities, services, receivers, and providers must validate inputs and enforce the authorization appropriate to the operation. Feed and launcher-settings access must preserve their intended signature protections.
- **Update integrity:** downloads must use HTTPS and validate the expected package, permitted version and channel, declared size and SHA-256, and trusted signing lineage before installation. Channel changes must not permit stale work or a version downgrade to bypass validation. Android's installation approval remains required.
- **Release integrity:** signing keys and publication credentials must remain private. Untrusted contributions must not gain access to those credentials or publish official artifacts. Release assets and update manifests must identify the intended verified artifact.

## Assessment and responsible testing

Report unauthorized data access, authentication or permission bypasses, exploitable component or input handling, update or signing-validation bypasses, credential exposure, and attacks that compromise official builds or distribution. Availability issues are relevant when an attacker can meaningfully disrupt the launcher.

Explain realistic attacker access, user interaction, affected builds, and impact. Automated scan results and dependency advisories are useful leads; include evidence of applicability when possible. A dependency's upstream origin or the existence of tests does not by itself dismiss a finding.

Test only on devices, accounts, and data you own or have permission to use. Use the minimum proof needed; do not access other users' data, perform destructive testing, or attack live build and publishing infrastructure.

Issues entirely within Android or an external service may require coordination with that project's maintainers. If LawnchairZ's integration is affected, report that impact here as well. The archived policy in `docs/reference/upstream-github/SECURITY.md` describes upstream Lawnchair and is not LawnchairZ's reporting channel.
