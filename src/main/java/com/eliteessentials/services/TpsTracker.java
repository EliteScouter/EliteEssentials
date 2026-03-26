package com.eliteessentials.services;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Tracks actual server TPS by measuring tick intervals on the world thread.
 * Schedules a lightweight task every second that executes on the world thread,
 * recording the nano timestamp of each execution. The delta between consecutive
 * executions gives us the real tick throughput.
 * 
 * Uses a rolling window of samples for a smooth average.
 */
public class TpsTracker {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final int SAMPLE_COUNT = 20;

    private static TpsTracker instance;

    private final long[] samples = new long[SAMPLE_COUNT];
    private int sampleIndex = 0;
    private long lastSampleTime = 0;
    private boolean ready = false;
    private double currentTps = 0.0;

    private ScheduledExecutorService scheduler;

    private TpsTracker() {}

    public static TpsTracker get() {
        if (instance == null) {
            instance = new TpsTracker();
        }
        return instance;
    }

    /**
     * Start tracking TPS. Call once during plugin startup.
     */
    public void start() {
        if (scheduler != null) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EE-TpsTracker");
            t.setDaemon(true);
            return t;
        });

        // Sample every second by scheduling a task on the world thread
        scheduler.scheduleAtFixedRate(() -> {
            try {
                World world = Universe.get().getWorld("default");
                if (world == null) return;

                // Execute on the world thread to get accurate tick timing
                world.execute(() -> {
                    long now = System.nanoTime();
                    if (lastSampleTime != 0) {
                        long delta = now - lastSampleTime;
                        samples[sampleIndex] = delta;
                        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;

                        if (!ready && sampleIndex == 0) {
                            ready = true;
                        }

                        // Calculate TPS from samples
                        recalculate();
                    }
                    lastSampleTime = now;
                });
            } catch (Exception ignored) {
                // World may not be loaded yet
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Stop tracking. Call during plugin shutdown.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void recalculate() {
        int count = ready ? SAMPLE_COUNT : sampleIndex;
        if (count == 0) return;

        long totalNanos = 0;
        for (int i = 0; i < count; i++) {
            totalNanos += samples[i];
        }

        // Average interval in seconds between our 1-second samples
        double avgIntervalSeconds = (totalNanos / (double) count) / 1_000_000_000.0;

        // Each sample interval should be ~1 second. The world's target TPS tells us
        // how many ticks should happen per second. If the interval is longer than 1s,
        // the server is lagging.
        if (avgIntervalSeconds > 0) {
            try {
                World world = Universe.get().getWorld("default");
                int targetTps = world != null ? world.getTps() : 30;
                // If our 1-second sample took avgIntervalSeconds, actual TPS is:
                currentTps = targetTps / avgIntervalSeconds;
                // Cap at target TPS
                if (currentTps > targetTps) currentTps = targetTps;
            } catch (Exception e) {
                currentTps = 0;
            }
        }
    }

    /**
     * Get the current measured TPS.
     * @return TPS value, or 0 if not enough samples yet
     */
    public double getTps() {
        return currentTps;
    }

    /**
     * Get TPS formatted as a string with one decimal place.
     */
    public String getTpsFormatted() {
        if (currentTps <= 0) return "--";
        return String.format("%.1f", currentTps);
    }
}
