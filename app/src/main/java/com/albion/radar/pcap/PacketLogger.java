package com.albion.radar.pcap;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Packet Logger for debugging Protocol18 parsing
 */
public class PacketLogger {

    private static final String TAG = "PacketLogger";
    public static final int MAX_PACKETS = 10000;

    private final Context context;
    private final File logDir;
    private PrintWriter logWriter;
    private boolean isLogging = false;
    private int loggedPackets = 0;
    private long startTime;

    public PacketLogger(Context context) {
        this.context = context;
        this.logDir = new File(context.getExternalFilesDir(null), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    public boolean startLogging() {
        if (isLogging) return true;

        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File logFile = new File(logDir, "packets_" + timestamp + ".log");
            logWriter = new PrintWriter(new FileWriter(logFile));
            isLogging = true;
            startTime = System.currentTimeMillis();
            loggedPackets = 0;

            logWriter.println("========================================");
            logWriter.println("Albion Radar Packet Log");
            logWriter.println("Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logWriter.println("Protocol: Photon GpBinaryV18");
            logWriter.println("========================================");
            logWriter.println();

            Log.i(TAG, "Started packet logging: " + logFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start logging: " + e.getMessage());
            return false;
        }
    }

    public void stopLogging() {
        if (!isLogging) return;

        if (logWriter != null) {
            logWriter.println();
            logWriter.println("========================================");
            logWriter.println("Stopped. Total packets: " + loggedPackets);
            logWriter.println("========================================");
            logWriter.close();
            logWriter = null;
        }

        isLogging = false;
        Log.i(TAG, "Stopped packet logging. Total: " + loggedPackets);
    }

    public synchronized void logRawPacket(byte[] data, int length, int srcPort, int dstPort) {
        if (!isLogging || logWriter == null || loggedPackets >= MAX_PACKETS) return;

        loggedPackets++;
        long timestamp = System.currentTimeMillis() - startTime;

        logWriter.println("--- Packet #" + loggedPackets + " [" + timestamp + "ms] ---");
        logWriter.println("Direction: " + (srcPort == 5056 ? "Server -> Client" : "Client -> Server"));
        logWriter.println("Length: " + length + " bytes");
        logWriter.println("Ports: " + srcPort + " -> " + dstPort);

        // Hex dump
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i % 16 == 0) {
                if (i > 0) {
                    logWriter.println("  " + hex.toString() + " | " + ascii.toString());
                    hex = new StringBuilder();
                    ascii = new StringBuilder();
                }
                logWriter.print(String.format("  %04X: ", i));
            }
            byte b = data[i];
            hex.append(String.format("%02X ", b & 0xFF));
            ascii.append(b >= 32 && b < 127 ? (char) b : '.');
        }
        if (hex.length() > 0) {
            int padding = 48 - hex.length();
            for (int i = 0; i < padding; i++) hex.append(" ");
            logWriter.println("  " + hex.toString() + " | " + ascii.toString());
        }
        logWriter.println();
    }

    public boolean isLogging() { return isLogging; }
    public int getLoggedPacketCount() { return loggedPackets; }
}
