package com.albion.radar.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.albion.radar.R;
import com.albion.radar.pcap.PCAPManager;
import com.albion.radar.pcap.PCAPdroidIntegration;

import java.io.File;

/**
 * Settings Activity
 *
 * Provides controls for PCAP recording, packet logging, and PCAPdroid integration.
 */
public class SettingsActivity extends AppCompatActivity {

    private PCAPManager pcapManager;
    private PCAPdroidIntegration pcapdroidIntegration;

    private Switch pcapSwitch;
    private Switch loggingSwitch;
    private TextView pcapStatusText;
    private TextView loggingStatusText;
    private TextView pcapdroidStatusText;
    private Button openPcapdroidBtn;
    private Button sharePcapBtn;
    private TextView pcapFilesText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pcapManager = new PCAPManager(this);
        pcapdroidIntegration = new PCAPdroidIntegration(this);

        initializeViews();
        updateStatus();
    }

    private void initializeViews() {
        pcapSwitch = findViewById(R.id.switch_pcap);
        loggingSwitch = findViewById(R.id.switch_logging);
        pcapStatusText = findViewById(R.id.text_pcap_status);
        loggingStatusText = findViewById(R.id.text_logging_status);
        pcapdroidStatusText = findViewById(R.id.text_pcapdroid_status);
        openPcapdroidBtn = findViewById(R.id.btn_open_pcapdroid);
        sharePcapBtn = findViewById(R.id.btn_share_pcap);
        pcapFilesText = findViewById(R.id.text_pcap_files);

        // PCAP switch
        pcapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                boolean started = pcapManager.startRecording();
                if (started) {
                    pcapStatusText.setText("Recording to: " + pcapManager.getCurrentPcapFile().getName());
                } else {
                    pcapSwitch.setChecked(false);
                    Toast.makeText(this, "Failed to start PCAP recording", Toast.LENGTH_SHORT).show();
                }
            } else {
                pcapManager.stopRecording();
                pcapStatusText.setText("Not recording");
            }
            updatePcapFilesList();
        });

        // Logging switch
        loggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                boolean started = pcapManager.startRecording();
                loggingStatusText.setText("Logging packets...");
            } else {
                pcapManager.stopRecording();
                loggingStatusText.setText("Logging stopped");
            }
        });

        // PCAPdroid button
        if (pcapdroidIntegration.isPCAPdroidInstalled()) {
            pcapdroidStatusText.setText("PCAPdroid installed: v" + pcapdroidIntegration.getPCAPdroidVersion());
            openPcapdroidBtn.setText("Open PCAPdroid");
            openPcapdroidBtn.setOnClickListener(v -> {
                Intent intent = pcapdroidIntegration.getAlbionCaptureIntent();
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // Fallback to just launching the app
                    startActivity(pcapdroidIntegration.getLaunchIntent());
                }
            });
        } else {
            pcapdroidStatusText.setText("PCAPdroid not installed");
            openPcapdroidBtn.setText("Install PCAPdroid");
            openPcapdroidBtn.setOnClickListener(v -> {
                startActivity(pcapdroidIntegration.getPlayStoreIntent());
            });
        }

        // Share PCAP button
        sharePcapBtn.setOnClickListener(v -> {
            Intent shareIntent = pcapManager.getShareIntent();
            if (shareIntent != null) {
                startActivity(shareIntent);
            } else {
                Toast.makeText(this, "No PCAP file to share", Toast.LENGTH_SHORT).show();
            }
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void updateStatus() {
        pcapSwitch.setChecked(pcapManager.isRecording());
        pcapStatusText.setText(pcapManager.isRecording() ?
            "Recording to: " + pcapManager.getCurrentPcapFile().getName() : "Not recording");
        updatePcapFilesList();
    }

    private void updatePcapFilesList() {
        File[] files = pcapManager.getPcapFiles();
        StringBuilder sb = new StringBuilder();
        sb.append("PCAP Files (").append(files.length).append("):\n");
        for (File file : files) {
            sb.append("  • ").append(file.getName())
              .append(" (").append(formatFileSize(file.length())).append(")\n");
        }
        pcapFilesText.setText(sb.toString());
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
