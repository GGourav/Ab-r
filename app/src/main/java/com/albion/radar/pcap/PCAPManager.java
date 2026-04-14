package com.albion.radar.pcap;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PCAP File Manager
 *
 * Handles PCAP file creation and export for packet analysis.
 * Integrates with PCAPdroid for enhanced packet capture capabilities.
 */
public class PCAPManager {

    private static final String TAG = "PCAPManager";

    // PCAP Global Header constants
    private static final int PCAP_MAGIC_NUMBER = 0xa1b2c3d4;
    private static final short PCAP_VERSION_MAJOR = 2;
    private static final short PCAP_VERSION_MINOR = 4;
    private static final int PCAP_THISZONE = 0;
    private static final int PCAP_SIGFIGS = 0;
    private static final int PCAP_SNAPLEN = 65535;
    private static final int PCAP_NETWORK = 1;  // Ethernet

    // PCAPdroid package name
    public static final String PCAPDROID_PACKAGE = "com.emanuelef.remote_capture";

    private Context context;
    private File currentPcapFile;
    private DataOutputStream pcapOutput;
    private boolean isRecording = false;
    private long captureStartTime;
    private int packetCount = 0;

    public PCAPManager(Context context) {
        this.context = context;
    }

    /**
     * Check if PCAPdroid is installed
     */
    public boolean isPCAPdroidInstalled() {
        try {
            context.getPackageManager().getPackageInfo(PCAPDROID_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Open PCAPdroid app
     */
    public Intent getOpenPCAPdroidIntent() {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(PCAPDROID_PACKAGE);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    /**
     * Start recording packets to PCAP file
     */
    public boolean startRecording() {
        if (isRecording) {
            return true;
        }

        try {
            // Create PCAP directory
            File pcapDir = new File(context.getExternalFilesDir(null), "pcap");
            if (!pcapDir.exists()) {
                pcapDir.mkdirs();
            }

            // Create timestamped file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentPcapFile = new File(pcapDir, "albion_" + timestamp + ".pcap");

            pcapOutput = new DataOutputStream(new FileOutputStream(currentPcapFile));

            // Write PCAP global header
            writeGlobalHeader();

            isRecording = true;
            captureStartTime = System.currentTimeMillis();
            packetCount = 0;

            Log.i(TAG, "Started PCAP recording: " + currentPcapFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start PCAP recording: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stop recording
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        try {
            if (pcapOutput != null) {
                pcapOutput.flush();
                pcapOutput.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing PCAP file: " + e.getMessage());
        }

        isRecording = false;
        Log.i(TAG, "Stopped PCAP recording. Packets: " + packetCount);
    }

    /**
     * Write UDP packet to PCAP file
     */
    public synchronized void writeUDPPacket(int srcPort, int dstPort, byte[] payload) {
        if (!isRecording || pcapOutput == null) {
            return;
        }

        try {
            // Calculate sizes
            int udpLength = 8 + payload.length;
            int ipLength = 20 + udpLength;
            int ethLength = 14 + ipLength;

            // Current timestamp
            long timestamp = System.currentTimeMillis() - captureStartTime;
            int tsSec = (int) (timestamp / 1000);
            int tsUsec = (int) ((timestamp % 1000) * 1000);

            // Write packet header
            pcapOutput.writeInt(tsSec);
            pcapOutput.writeInt(tsUsec);
            pcapOutput.writeInt(ethLength);
            pcapOutput.writeInt(ethLength);

            // Write Ethernet header (14 bytes)
            byte[] ethHeader = new byte[14];
            ethHeader[0] = (byte) 0xff;
            ethHeader[1] = (byte) 0xff;
            ethHeader[2] = (byte) 0xff;
            ethHeader[3] = (byte) 0xff;
            ethHeader[4] = (byte) 0xff;
            ethHeader[5] = (byte) 0xff;
            ethHeader[6] = 0x00;
            ethHeader[7] = 0x00;
            ethHeader[8] = 0x00;
            ethHeader[9] = 0x00;
            ethHeader[10] = 0x00;
            ethHeader[11] = 0x01;
            ethHeader[12] = 0x08;
            ethHeader[13] = 0x00;
            pcapOutput.write(ethHeader);

            // Write IP header (20 bytes)
            byte[] ipHeader = new byte[20];
            ipHeader[0] = 0x45;
            ipHeader[1] = 0x00;
            ipHeader[2] = (byte) ((ipLength >> 8) & 0xFF);
            ipHeader[3] = (byte) (ipLength & 0xFF);
            ipHeader[8] = 0x40;
            ipHeader[9] = 0x11;
            ipHeader[12] = (byte) 192;
            ipHeader[13] = (byte) 168;
            ipHeader[14] = 1;
            ipHeader[15] = 1;
            ipHeader[16] = 5;
            ipHeader[17] = (byte) 188;
            ipHeader[18] = 0;
            ipHeader[19] = 1;
            pcapOutput.write(ipHeader);

            // Write UDP header
            pcapOutput.writeShort((short) srcPort);
            pcapOutput.writeShort((short) dstPort);
            pcapOutput.writeShort((short) udpLength);
            pcapOutput.writeShort((short) 0);

            // Write payload
            pcapOutput.write(payload);
            pcapOutput.flush();
            packetCount++;

        } catch (IOException e) {
            Log.e(TAG, "Error writing UDP packet: " + e.getMessage());
        }
    }

    /**
     * Write PCAP global header
     */
    private void writeGlobalHeader() throws IOException {
        pcapOutput.writeInt(PCAP_MAGIC_NUMBER);
        pcapOutput.writeShort(PCAP_VERSION_MAJOR);
        pcapOutput.writeShort(PCAP_VERSION_MINOR);
        pcapOutput.writeInt(PCAP_THISZONE);
        pcapOutput.writeInt(PCAP_SIGFIGS);
        pcapOutput.writeInt(PCAP_SNAPLEN);
        pcapOutput.writeInt(PCAP_NETWORK);
    }

    /**
     * Get current PCAP file
     */
    public File getCurrentPcapFile() {
        return currentPcapFile;
    }

    /**
     * Get packet count
     */
    public int getPacketCount() {
        return packetCount;
    }

    /**
     * Check if recording
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Get PCAP file Uri for sharing
     */
    public Uri getPcapFileUri() {
        if (currentPcapFile != null && currentPcapFile.exists()) {
            return Uri.fromFile(currentPcapFile);
        }
        return null;
    }

    /**
     * Share PCAP file via intent
     */
    public Intent getShareIntent() {
        if (currentPcapFile == null || !currentPcapFile.exists()) {
            return null;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/vnd.tcpdump.pcap");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(currentPcapFile));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return Intent.createChooser(shareIntent, "Share PCAP file");
    }

    /**
     * Get all PCAP files
     */
    public File[] getPcapFiles() {
        File pcapDir = new File(context.getExternalFilesDir(null), "pcap");
        if (pcapDir.exists()) {
            return pcapDir.listFiles((dir, name) -> name.endsWith(".pcap"));
        }
        return new File[0];
    }
}
