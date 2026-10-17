# Google Discover inside LawnchairZ

LawnchairZ shows Google's native Discover overlay beside the first Home page. Setup and the
feature switch live in **Home settings → Home screen → Google Discover**. There is no separate
feed screen or helper icon in the app drawer.

The setup flow and the bundled helper come from Expressive Launcher. LawnchairZ keeps them,
renamed (see [source provenance](SOURCE_PROVENANCE.md)), because Lawnchair's own Lawnfeed
bridge only accepts Lawnchair's application IDs.

## Setup and behavior

1. The Google app must be installed and enabled. Settings offers the appropriate Google-app action
   if it is missing or disabled.
2. Choose **Set up Google Discover**. LawnchairZ verifies and extracts its included background
   support; Android asks for install-source permission if needed and confirms the installation.
3. After installation, **Show Google Discover** controls the native panel. Swipe right from the
   first Home page (left in RTL). Feed rendering, content and Google account behavior come from
   the installed Google app.

The setup survives returning from permission settings and refreshes after package installation,
removal, replacement, disabling or re-enabling. Cancelled setup can be retried. Existing Home data
is preserved. The switch is enabled by a completed, explicitly requested setup; merely inspecting
settings does not change the user's preference.

New launcher versions include matching support updates. **Update Discover support** runs the same
verified Android installation flow. An already newer compatible helper is retained. A disabled or
differently signed helper requires attention in Android App info; LawnchairZ does not silently
uninstall it or bypass Android's signature rules.

## Packaging and trust

The helper is the `:lawnchairzFeed` module, application ID `dev.launcher.lawnchairz.feed`. The
launcher embeds it as `feed-helper/LawnchairZFeed.apk` with `feed-helper/metadata.json`. AGP
builds the helper before merging launcher assets, using helper Debug for launcher Debug and
helper Release for Qa/Release. The version, size and SHA-256 come from the actual built artifact.
No binary is checked into source and no additional download is required for feed support.

Before proposing an installation, LawnchairZ validates the bundled bytes, package/version,
signing certificate against its own installed certificate, and the protected bridge service
(`FeedBridgeService`). Helpers installed before the rename expose the same bridge as
`ExpressiveFeedBridgeService`, which setup also accepts until the helper is updated. The APK is
shared only through the FileProvider's dedicated `feed-helper/` cache path. Android remains
responsible for permission and installation confirmation.

The main Qa and Release APKs are non-debuggable. The small helper deliberately has its own
debuggable application identity and accepts only same-signed LawnchairZ callers through a
signature permission and UID validation. Android lists it as **LawnchairZ Discover support** in
installed applications, despite its lack of launcher icon or separate UI.

## Google compatibility boundary

Google has no stable public Discover-overlay SDK for third-party launchers. Other launcher
maintainers document the companion approach, including [Nova](https://novalauncher.com/nowcompanion/)
and [Smart Launcher](https://www.smartlauncher.net/bridge). Android's `debuggable` flag applies to
the [whole application](https://developer.android.com/guide/topics/manifest/application-element),
so placing the bridge in another process inside the launcher would not reproduce its independent
application identity. Expressive Launcher's compatibility probe found that Google's overlay
returned a null binding to a non-debuggable app and a working native Discover window to a
debuggable one.

Google-app updates, account state and network availability can affect the panel. The local setup
status confirms installation prerequisites; it does not claim live content has loaded.
