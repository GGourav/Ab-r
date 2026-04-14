package com.albion.radar.service;

import android.os.Binder;

/**
 * Binder for PacketCaptureService
 */
public class LocalBinder extends Binder {
    
    private final PacketCaptureService service;

    public LocalBinder(PacketCaptureService service) {
        this.service = service;
    }

    public PacketCaptureService getService() {
        return service;
    }
}
