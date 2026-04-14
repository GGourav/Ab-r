package com.albion.radar.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.albion.radar.R;
import com.albion.radar.entity.Entity;
import com.albion.radar.entity.EntityManager;
import com.albion.radar.parser.EntityUpdate;
import com.albion.radar.service.PacketCaptureService;

import java.util.List;
import java.util.Locale;

/**
 * Main Radar Activity
 *
 * Displays the radar overlay with entity positions
 */
public class RadarActivity extends Activity implements PacketCaptureService.PacketListener {

    private static final String TAG = "RadarActivity";
    private static final int VPN_REQUEST_CODE = 0x0F;

    // Views
    private ImageView radarView;
    private TextView statsText;
    private TextView entityCountText;
    private FrameLayout radarContainer;

    // Radar settings
    private int radarSize = 400;  // pixels
    private float radarRange = 50.0f;  // game units

    // Drawing
    private Bitmap radarBitmap;
    private Canvas radarCanvas;
    private Paint paint;
    private Paint textPaint;
    private Paint outlinePaint;

    // Entity manager
    private EntityManager entityManager;

    // Service
    private PacketCaptureService captureService;
    private boolean serviceBound = false;

    // Statistics
    private long lastUpdateTime = 0;
    private int fps = 0;
    private int frameCount = 0;

    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PacketCaptureService.LocalBinder binder = (PacketCaptureService.LocalBinder) service;
            captureService = binder.getService();
            captureService.setPacketListener(RadarActivity.this);
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            captureService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_radar);

        // Initialize views
        radarView = findViewById(R.id.radar_view);
        statsText = findViewById(R.id.stats_text);
        entityCountText = findViewById(R.id.entity_count);
        radarContainer = findViewById(R.id.radar_container);

        // Initialize entity manager
        entityManager = EntityManager.getInstance();

        // Initialize drawing
        initializeDrawing();

        // Settings button
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Request VPN permission and start service
        requestVpnPermission();

        // Start render thread
        startRenderThread();
    }

    /**
     * Initialize drawing components
     */
    private void initializeDrawing() {
        // Create radar bitmap
        radarBitmap = Bitmap.createBitmap(radarSize, radarSize, Bitmap.Config.ARGB_8888);
        radarCanvas = new Canvas(radarBitmap);

        // Initialize paints
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(3);
    }

    /**
     * Request VPN permission
     */
    private void requestVpnPermission() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            startCaptureService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startCaptureService();
            } else {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Start the packet capture service
     */
    private void startCaptureService() {
        Intent intent = new Intent(this, PacketCaptureService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Start the render thread
     */
    private void startRenderThread() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(50);  // 20 FPS target

                    runOnUiThread(() -> renderRadar());

                    // FPS calculation
                    frameCount++;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime >= 1000) {
                        fps = frameCount;
                        frameCount = 0;
                        lastUpdateTime = now;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * Render the radar
     */
    private void renderRadar() {
        // Clear canvas
        radarBitmap.eraseColor(Color.TRANSPARENT);

        // Draw background
        drawRadarBackground();

        // Draw entities
        List<Entity> entities = entityManager.getVisibleEntities();
        for (Entity entity : entities) {
            drawEntity(entity);
        }

        // Draw local player at center
        drawLocalPlayer();

        // Draw compass
        drawCompass();

        // Update view
        radarView.setImageBitmap(radarBitmap);

        // Update stats
        updateStats(entities.size());
    }

    /**
     * Draw radar background
     */
    private void drawRadarBackground() {
        // Draw semi-transparent background
        paint.setColor(0xCC000000);  // 80% black
        paint.setStyle(Paint.Style.FILL);
        radarCanvas.drawCircle(radarSize / 2f, radarSize / 2f, radarSize / 2f, paint);

        // Draw grid
        paint.setColor(0x44FFFFFF);  // 25% white
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);

        // Concentric circles
        for (int i = 1; i <= 4; i++) {
            float radius = (radarSize / 2f) * (i / 4f);
            radarCanvas.drawCircle(radarSize / 2f, radarSize / 2f, radius, paint);
        }

        // Cross lines
        float center = radarSize / 2f;
        radarCanvas.drawLine(center, 0, center, radarSize, paint);
        radarCanvas.drawLine(0, center, radarSize, center, paint);
    }

    /**
     * Draw an entity on the radar
     */
    private void drawEntity(Entity entity) {
        if (!entity.hasValidPosition) return;

        // Calculate position on radar
        float localX = entityManager.getLocalPosX();
        float localY = entityManager.getLocalPosY();

        float dx = entity.posX - localX;
        float dy = entity.posY - localY;

        // Check if in range
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > radarRange) return;

        // Convert to radar coordinates
        float radarX = (radarSize / 2f) + (dx / radarRange) * (radarSize / 2f);
        float radarY = (radarSize / 2f) - (dy / radarRange) * (radarSize / 2f);

        // Clamp to radar bounds
        radarX = Math.max(10, Math.min(radarSize - 10, radarX));
        radarY = Math.max(10, Math.min(radarSize - 10, radarY));

        // Get color based on entity type
        int color = getEntityColor(entity);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        // Draw entity marker
        float markerSize = getMarkerSize(entity);

        if (entity.isBoss()) {
            // Draw larger marker with border for bosses
            paint.setColor(Color.RED);
            radarCanvas.drawCircle(radarX, radarY, markerSize + 2, paint);
            paint.setColor(color);
            radarCanvas.drawCircle(radarX, radarY, markerSize, paint);
        } else if (entity.isEnchanted() || entity.rarity > 0) {
            // Draw with enchantment outline
            paint.setColor(getEnchantmentOutlineColor(entity));
            radarCanvas.drawCircle(radarX, radarY, markerSize + 2, paint);
            paint.setColor(color);
            radarCanvas.drawCircle(radarX, radarY, markerSize - 1, paint);
        } else {
            radarCanvas.drawCircle(radarX, radarY, markerSize, paint);
        }

        // Draw tier indicator for harvestables
        if (entity.isHarvestable() && entity.tier > 0) {
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(16);
            radarCanvas.drawText(String.valueOf(entity.tier), radarX, radarY + 5, textPaint);
        }

        // Draw name for players
        if (entity.isPlayer() && entity.name != null) {
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(14);
            String displayName = entity.name.length() > 10 ?
                entity.name.substring(0, 10) : entity.name;
            radarCanvas.drawText(displayName, radarX, radarY - markerSize - 5, textPaint);
        }
    }

    /**
     * Draw local player marker at center
     */
    private void drawLocalPlayer() {
        float center = radarSize / 2f;

        // Draw player marker
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.FILL);
        radarCanvas.drawCircle(center, center, 6, paint);

        // Draw direction indicator
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        radarCanvas.drawLine(center, center, center, center - 15, paint);
    }

    /**
     * Draw compass directions
     */
    private void drawCompass() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(14);

        float center = radarSize / 2f;
        float offset = radarSize / 2f - 15;

        radarCanvas.drawText("N", center, 20, textPaint);
        radarCanvas.drawText("S", center, radarSize - 8, textPaint);
        radarCanvas.drawText("E", radarSize - 10, center + 5, textPaint);
        radarCanvas.drawText("W", 10, center + 5, textPaint);
    }

    /**
     * Get entity color based on type and tier
     */
    private int getEntityColor(Entity entity) {
        if (entity.isPlayer()) {
            return Color.YELLOW;
        }

        if (entity.isBoss()) {
            return Color.RED;
        }

        if (entity.isMob()) {
            return 0xFFFF6600;  // Orange for mobs
        }

        if (entity.isHarvestable() || entity.isLivingHarvestable()) {
            return entity.getTierColor();
        }

        return Color.GRAY;
    }

    /**
     * Get enchantment outline color
     */
    private int getEnchantmentOutlineColor(Entity entity) {
        if (entity.rarity > 0) {
            return entity.getRarityColor();
        }
        return entity.getEnchantmentColor();
    }

    /**
     * Get marker size based on entity type
     */
    private float getMarkerSize(Entity entity) {
        if (entity.isBoss()) {
            return 8;
        }
        if (entity.isPlayer()) {
            return 5;
        }
        if (entity.isMob()) {
            return 4;
        }
        if (entity.isHarvestable()) {
            return 3 + (entity.tier * 0.5f);
        }
        return 4;
    }

    /**
     * Update statistics display
     */
    private void updateStats(int entityCount) {
        String stats = String.format(Locale.getDefault(),
            "FPS: %d | Entities: %d", fps, entityCount);
        statsText.setText(stats);
        entityCountText.setText(String.valueOf(entityCount));
    }

    // PacketCaptureService.PacketListener callbacks

    @Override
    public void onEntityUpdate(EntityUpdate update) {
        // Already handled by EntityManager
    }

    @Override
    public void onPacketCaptured(byte[] data, int length, int srcPort, int dstPort) {
        // Optional: raw packet logging
    }

    @Override
    public void onStatisticsUpdate(long captured, long processed) {
        // Optional: update packet statistics
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        Intent intent = new Intent(this, PacketCaptureService.class);
        stopService(intent);

        super.onDestroy();
    }
}
