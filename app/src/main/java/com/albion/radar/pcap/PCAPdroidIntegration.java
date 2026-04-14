package com.albion.radar.pcap;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

/**
 * PCAPdroid Integration Helper
 */
public class PCAPdroidIntegration {

    private static final String TAG = "PCAPdroidIntegration";

    public static final String PCAPDROID_PACKAGE = "com.emanuelef.remote_capture";
    public static final String ALBION_PACKAGE = "com.sandboxol.albiononline";

    private final Context context;

    public PCAPdroidIntegration(Context context) {
        this.context = context;
    }

    public boolean isPCAPdroidInstalled() {
        try {
            context.getPackageManager().getPackageInfo(PCAPDROID_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public String getPCAPdroidVersion() {
        try {
            return context.getPackageManager().getPackageInfo(PCAPDROID_PACKAGE, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public Intent getPlayStoreIntent() {
        try {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PCAPDROID_PACKAGE));
        } catch (Exception e) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + PCAPDROID_PACKAGE));
        }
    }

    public Intent getLaunchIntent() {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(PCAPDROID_PACKAGE);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public Intent getAlbionCaptureIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(PCAPDROID_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public boolean isAlbionInstalled() {
        try {
            context.getPackageManager().getPackageInfo(ALBION_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public String getStatusMessage() {
        if (!isPCAPdroidInstalled()) {
            return "PCAPdroid not installed. Install from Play Store for advanced capture.";
        }
        if (!isAlbionInstalled()) {
            return "Albion Online not detected. Start the game first.";
        }
        return "Ready to capture Albion traffic on UDP port 5056.";
    }
}
