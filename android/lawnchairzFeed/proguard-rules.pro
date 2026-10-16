# The service and generated AIDL stubs are reached from the manifest/Binder entry points.
# LawnchairZ modification: the service is now dev.launcher.lawnchairz.feed.FeedBridgeService.
-keep class dev.launcher.lawnchairz.feed.FeedBridgeService { *; }
-keep class amirz.aidlbridge.** { *; }
