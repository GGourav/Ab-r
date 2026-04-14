package com.albion.radar.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.albion.radar.R;
import com.albion.radar.entity.EntityManager;
import com.albion.radar.parser.AlbionEntityParser;
import com.albion.radar.parser.EntityUpdate;
import com.albion.radar.parser.PhotonPacketParser;
import com.albion.radar.parser.Protocol18Parser;
import com.albion.radar.pcap.PCAPManager;
import com.albion.radar.pcap.PacketLogger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Hashtable;
import java.util.List;

/**
 * Packet Capture Service using VPN API
 *
 * This service captures network packets without root access
 * by creating a VPN interface that intercepts all traffic.
 *
 * For Albion Online, we filter UDP port 5056 (Photon protocol)
 */
public class PacketCaptureService extends VpnService {

    private static final String TAG = "PacketCaptureService";
    private static final String CHANNEL_ID = "AlbionRadarChannel";

    // Albion Online server info
    private static final int ALBION_PORT = 5056;
    private static final String ALBION_IP_PREFIX = "5.188.";  // Albion server IP range

    private ParcelFileDescriptor vpnInterface;
    private FileInputStream vpnInput;
    private FileOutputStream vpnOutput;
    private volatile boolean running = false;
    private Thread captureThread;

    private EntityManager entityManager;
    private PacketListener packetListener;

    // Statistics
    private long packetsCaptured = 0;
    private long packetsProcessed = 0;
    private long startTime;

    // PCAP and Logging
    private PCAPManager pcapManager;
    private PacketLogger packetLogger;
    private boolean pcapEnabled = false;
    private boolean loggingEnabled = false;

    // Binder for service binding
    private final IBinder binder = new LocalBinder(this);

    public interface PacketListener {
        void onEntityUpdate(EntityUpdate update);
        void onPacketCaptured(byte[] data, int length, int srcPort, int dstPort);
        void onStatisticsUpdate(long captured, long processed);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        entityManager = EntityManager.getInstance();
        pcapManager = new PCAPManager(this);
        packetLogger = new PacketLogger(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            startCapture();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    /**
     * Start packet capture
     */
    private void startCapture() {
        running = true;
        startTime = System.currentTimeMillis();

        // Start foreground service with notification
        startForeground(1, createNotification());

        // Configure VPN interface
        Builder builder = new Builder();
        builder.setSession("Albion Radar");
        builder.addAddress("10.0.0.2", 32);  // Local VPN address
        builder.addRoute("0.0.0.0", 0);  // Route all traffic
        builder.addDnsServer("8.8.8.8");

        // Try to exclude our app from VPN to avoid loops
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception e) {
                Log.w(TAG, "Could not exclude self from VPN");
            }
        }

        try {
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                stopSelf();
                return;
            }

            vpnInput = new FileInputStream(vpnInterface.getFileDescriptor());
            vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor());

            // Start capture thread
            captureThread = new Thread(this::captureLoop);
            captureThread.start();

            Log.i(TAG, "Packet capture started");

        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN: " + e.getMessage());
            stopSelf();
        }
    }

    /**
     * Main capture loop
     */
    private void captureLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(32767);

        while (running && vpnInput != null) {
            try {
                // Read packet from VPN interface
                int length = vpnInput.read(buffer.array());

                if (length > 0) {
                    packetsCaptured++;

                    // Parse IP packet
                    buffer.limit(length);
                    buffer.rewind();

                    // Check if this is UDP packet
                    int protocol = buffer.get(9) & 0xFF;

                    if (protocol == 17) {  // UDP
                        // Extract UDP header
                        int ipHeaderLength = (buffer.get(0) & 0x0F) * 4;
                        int srcPort = ((buffer.get(ipHeaderLength) & 0xFF) << 8) |
                                      (buffer.get(ipHeaderLength + 1) & 0xFF);
                        int dstPort = ((buffer.get(ipHeaderLength + 2) & 0xFF) << 8) |
                                      (buffer.get(ipHeaderLength + 3) & 0xFF);

                        // Check if it's Albion traffic
                        if (srcPort == ALBION_PORT || dstPort == ALBION_PORT) {
                            // Extract payload
                            int udpHeaderLength = 8;
                            int payloadOffset = ipHeaderLength + udpHeaderLength;
                            int payloadLength = length - payloadOffset;

                            if (payloadLength > 0 && payloadLength < 65536) {
                                byte[] payload = new byte[payloadLength];
                                buffer.position(payloadOffset);
                                buffer.get(payload);

                                // Log raw packet
                                if (loggingEnabled && packetLogger.isLogging()) {
                                    packetLogger.logRawPacket(payload, payloadLength, srcPort, dstPort);
                                }

                                // Write to PCAP
                                if (pcapEnabled && pcapManager.isRecording()) {
                                    pcapManager.writeUDPPacket(srcPort, dstPort, payload);
                                }

                                // Process the payload
                                processPacket(payload, srcPort, dstPort);

                                if (packetListener != null) {
                                    packetListener.onPacketCaptured(payload, payloadLength, srcPort, dstPort);
                                }
                            }
                        }
                    }

                    // Forward packet to actual network
                    // (VPN intercepts, we need to pass through)
                    vpnOutput.write(buffer.array(), 0, length);
                    vpnOutput.flush();
                }

                buffer.clear();

            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Error in capture loop: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Process a captured packet
     */
    private void processPacket(byte[] payload, int srcPort, int dstPort) {
        try {
            // Parse Photon packet
            PhotonPacketParser photonParser = new PhotonPacketParser(payload);
            List<PhotonPacketParser.PhotonMessage> messages = photonParser.parse();

            for (PhotonPacketParser.PhotonMessage message : messages) {
                if (message.payload != null && message.payload.length > 0) {
                    // Parse Protocol18 payload
                    Protocol18Parser protoParser = new Protocol18Parser(message.payload);

                    if (protoParser.hasRemaining()) {
                        byte messageType = protoParser.readByte();

                        // Check for event message
                        if (messageType == PhotonPacketParser.MESSAGE_TYPE_EVENT) {
                            Protocol18Parser.EventData eventData = protoParser.readEventData();

                            if (eventData != null && eventData.parameters != null) {
                                // Parse Albion-specific event
                                EntityUpdate update = AlbionEntityParser.parseEvent(
                                    eventData.eventCode,
                                    eventData.parameters
                                );

                                if (update != null) {
                                    packetsProcessed++;

                                    // Update entity manager
                                    entityManager.processUpdate(update);

                                    // Notify listener
                                    if (packetListener != null) {
                                        packetListener.onEntityUpdate(update);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Update statistics periodically
            if (packetsCaptured % 100 == 0 && packetListener != null) {
                packetListener.onStatisticsUpdate(packetsCaptured, packetsProcessed);
            }

        } catch (Exception e) {
            Log.d(TAG, "Packet processing error: " + e.getMessage());
        }
    }

    /**
     * Stop packet capture
     */
    private void stopCapture() {
        running = false;

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        try {
            if (vpnInput != null) {
                vpnInput.close();
                vpnInput = null;
            }
            if (vpnOutput != null) {
                vpnOutput.close();
                vpnOutput = null;
            }
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing VPN: " + e.getMessage());
        }

        Log.i(TAG, "Packet capture stopped. Captured: " + packetsCaptured +
              ", Processed: " + packetsProcessed);
    }

    /**
     * Create notification channel for foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Albion Radar",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Packet capture for Albion Radar");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Create foreground notification
     */
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_radar)
               .setContentTitle("Albion Radar Active")
               .setContentText("Capturing game packets...")
               .setOngoing(true);

        return builder.build();
    }

    /**
     * Set packet listener
     */
    public void setPacketListener(PacketListener listener) {
        this.packetListener = listener;
    }

    /**
     * Get capture statistics
     */
    public String getStatistics() {
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return String.format("Captured: %d, Processed: %d, Duration: %ds",
            packetsCaptured, packetsProcessed, duration);
    }

    /**
     * Check if capture is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Enable/disable PCAP recording
     */
    public void setPcapEnabled(boolean enabled) {
        this.pcapEnabled = enabled;
        if (enabled && running) {
            pcapManager.startRecording();
        } else if (!enabled) {
            pcapManager.stopRecording();
        }
    }

    /**
     * Enable/disable packet logging
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        if (enabled && running) {
            packetLogger.startLogging();
        } else if (!enabled) {
            packetLogger.stopLogging();
        }
    }

    /**
     * Get PCAP manager
     */
    public PCAPManager getPcapManager() {
        return pcapManager;
    }

    /**
     * Get packet logger
     */
    public PacketLogger getPacketLogger() {
        return packetLogger;
    }

    /**
     * Check if PCAP recording is active
     */
    public boolean isPcapRecording() {
        return pcapManager.isRecording();
    }

    /**
     * Check if packet logging is active
     */
    public boolean isPacketLogging() {
        return packetLogger.isLogging();
    }
    }
