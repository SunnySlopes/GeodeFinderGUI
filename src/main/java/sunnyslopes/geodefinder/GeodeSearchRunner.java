package sunnyslopes.geodefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs {@link GeodeFinderBridge#geodeSearch} off the UI thread and polls progress.
 */
public class GeodeSearchRunner {

    public static final String[] VERSIONS = {
        "1.17.x",
        "1.18.0~1.18.1",
        "1.18.2~1.19.3",
        "1.19.4~26.2",
        "26.3+",
    };
    public static final int DEFAULT_VERSION = 4;
    public static final int MODE_FULL = 0;
    public static final int MODE_STRUCTURE = 1;
    public static final int MODE_FAST = 2;

    public static final long MAX_SEARCH_AREA_BLOCKS = 60_000_001L * 60_000_001L;
    private static final long PROGRESS_POLL_MS = 100L;

    public record ProgressInfo(int phase, long processed, long total, double percentage, long elapsedMs,
                               long remainingMs, boolean done, boolean nativePauseSettled, boolean nativeStopRequested) {
        public static ProgressInfo of(int phase, long processed, long total, long elapsedMs, long remainingMs,
                                      boolean nativePauseSettled, boolean nativeStopRequested) {
            double pct = total > 0 ? (processed * 100.0 / total) : 0;
            return new ProgressInfo(phase, processed, total, pct, elapsedMs, remainingMs, false,
                nativePauseSettled, nativeStopRequested);
        }

        public static ProgressInfo terminal(long elapsedMs, long processed, long total) {
            long t = Math.max(1, total);
            long p = Math.min(processed, t);
            double pct = t > 0 ? (p * 100.0 / t) : 100.0;
            return new ProgressInfo(0, p, t, pct, elapsedMs, 0, true, false, false);
        }
    }

    public static boolean isNativePauseSettled(int[] progress) {
        if (progress == null || progress.length < 4) {
            return false;
        }
        boolean tryPause = progress.length > 4 && progress[4] != 0;
        return tryPause && progress[3] == 1;
    }

    public static int blockToChunk(int block) {
        return Math.floorDiv(block, 16);
    }

    public static String formatHits(int[] raw) {
        List<int[]> hits = decode(raw);
        sortHits(hits);
        StringBuilder sb = new StringBuilder();
        for (int[] hit : hits) {
            sb.append(formatHit(hit[0], hit[1], hit[2], hit[3])).append('\n');
        }
        return sb.toString();
    }

    public static String formatHit(int blockX, int blockZ, int budding, int geodeCount) {
        if (budding < 0) {
            return "/tp " + blockX + " 64 " + blockZ + " geodes=" + geodeCount;
        }
        return "/tp " + blockX + " 64 " + blockZ + " budding=" + budding + " geodes=" + geodeCount;
    }

    public static void sortHits(List<int[]> hits) {
        hits.sort(Comparator
            .comparingInt((int[] hit) -> hit[2] >= 0 ? hit[2] : hit[3]).reversed()
            .thenComparingLong(hit -> (long) hit[0] * hit[0] + (long) hit[1] * hit[1])
            .thenComparingInt(hit -> hit[1])
            .thenComparingInt(hit -> hit[0]));
    }

    private static List<int[]> decode(int[] raw) {
        List<int[]> hits = new ArrayList<>();
        if (raw == null) {
            return hits;
        }
        for (int i = 0; i + 3 < raw.length; i += 4) {
            hits.add(new int[] {raw[i], raw[i + 1], raw[i + 2], raw[i + 3]});
        }
        return hits;
    }

    private static final Object NATIVE_LOCK = new Object();
    private static volatile GeodeSearchRunner activeRunner;

    public static boolean isAnySearchActive() {
        synchronized (NATIVE_LOCK) {
            return activeRunner != null;
        }
    }

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile long runStartTimeMs = 0;
    private volatile long totalPausedMs = 0;
    private volatile long pauseStartMs = 0;

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartMs = System.currentTimeMillis();
            try {
                GeodeFinderBridge.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    public void resume() {
        if (isPaused) {
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            try {
                GeodeFinderBridge.resume();
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        try {
            GeodeFinderBridge.stop();
        } catch (Throwable ignored) {
        }
    }

    private long getElapsedMs() {
        long paused = totalPausedMs;
        if (pauseStartMs > 0) {
            paused += System.currentTimeMillis() - pauseStartMs;
        }
        return Math.max(0, System.currentTimeMillis() - runStartTimeMs - paused);
    }

    public boolean startSearch(long seed, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                               int geodeThreshold, int buddingThreshold, int loadedRadius, int threadCount,
                               int version, int searchMode, boolean publishPartial,
                               Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback) {
        if (!tryAcquireActiveRunner()) {
            return false;
        }
        Thread thread = new Thread(() -> runSearch(seed, minChunkX, maxChunkX, minChunkZ, maxChunkZ, geodeThreshold,
            buddingThreshold, loadedRadius, threadCount, version, searchMode, publishPartial, progressCallback,
            resultCallback), "geodefinder-search");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public boolean runSearchBlocking(long seed, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                     int geodeThreshold, int buddingThreshold, int loadedRadius, int threadCount,
                                     int version, int searchMode, Consumer<ProgressInfo> progressCallback,
                                     Consumer<String> resultCallback) {
        if (!tryAcquireActiveRunner()) {
            return false;
        }
        runSearch(seed, minChunkX, maxChunkX, minChunkZ, maxChunkZ, geodeThreshold, buddingThreshold, loadedRadius,
            threadCount, version, searchMode, false, progressCallback, resultCallback);
        return true;
    }

    private boolean tryAcquireActiveRunner() {
        synchronized (NATIVE_LOCK) {
            if (activeRunner != null) {
                return false;
            }
            activeRunner = this;
            return true;
        }
    }

    private void releaseActiveRunner() {
        synchronized (NATIVE_LOCK) {
            if (activeRunner == this) {
                activeRunner = null;
            }
        }
    }

    private void runSearch(long seed, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                           int geodeThreshold, int buddingThreshold, int loadedRadius, int threadCount,
                           int version, int searchMode, boolean publishPartial,
                           Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback) {
        isRunning = true;
        isPaused = false;
        totalPausedMs = 0;
        pauseStartMs = 0;
        runStartTimeMs = System.currentTimeMillis();
        int threads = Math.max(1, threadCount);

        long searchWidth = (long) maxChunkX - minChunkX + 1;
        long searchHeight = (long) maxChunkZ - minChunkZ + 1;
        long chunkArea = searchWidth * searchHeight;
        if (chunkArea <= 0 || chunkArea > MAX_SEARCH_AREA_BLOCKS) {
            isRunning = false;
            releaseActiveRunner();
            if (resultCallback != null) {
                resultCallback.accept("[Error] Search area too large.");
            }
            if (progressCallback != null) {
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), 0, 1));
            }
            return;
        }

        AtomicLong lastProcessed = new AtomicLong(0);
        AtomicLong lastTotal = new AtomicLong(0);
        AtomicLong phase2StartElapsedMs = new AtomicLong(-1L);
        StringBuilder lastPartial = new StringBuilder();

        ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "geodefinder-progress");
            thread.setDaemon(true);
            return thread;
        });

        progressScheduler.scheduleAtFixedRate(() -> {
            if (!isRunning || activeRunner != GeodeSearchRunner.this) {
                return;
            }
            try {
                int[] arr = GeodeFinderBridge.getSearchProgress();
                if (arr == null || arr.length < 4 || progressCallback == null) {
                    return;
                }
                boolean tryStop = arr.length > 5 && arr[5] != 0;
                boolean pauseSettled = isNativePauseSettled(arr);
                int cur = arr[0];
                int tot = arr[1];
                int phase = arr[2];
                if (tot <= 0) {
                    return;
                }
                long elapsed = getElapsedMs();
                long phaseElapsed = elapsed;
                if (phase == 2) {
                    phase2StartElapsedMs.compareAndSet(-1L, elapsed);
                    phaseElapsed = Math.max(0L, elapsed - Math.max(0L, phase2StartElapsedMs.get()));
                }
                lastProcessed.set(cur);
                lastTotal.set(tot);
                long remaining = 0L;
                if (!isPaused && tot > cur && cur > 0 && phaseElapsed > 0) {
                    double eta = phaseElapsed * ((double) (tot - cur) / (double) cur);
                    if (eta > 0 && eta < (double) Long.MAX_VALUE) {
                        remaining = (long) eta;
                    }
                }
                progressCallback.accept(ProgressInfo.of(phase, cur, tot, elapsed, remaining, pauseSettled, tryStop));
                if (publishPartial && phase == 2 && resultCallback != null) {
                    String snapshot = formatHits(GeodeFinderBridge.getNowResult());
                    if (!snapshot.contentEquals(lastPartial)) {
                        lastPartial.setLength(0);
                        lastPartial.append(snapshot);
                        resultCallback.accept(snapshot);
                    }
                }
            } catch (Throwable ignored) {
            }
        }, PROGRESS_POLL_MS, PROGRESS_POLL_MS, TimeUnit.MILLISECONDS);

        try {
            int[] raw;
            synchronized (NATIVE_LOCK) {
                raw = GeodeFinderBridge.geodeSearch(seed, minChunkX, minChunkZ, maxChunkX, maxChunkZ,
                    geodeThreshold, buddingThreshold, loadedRadius, threads, version, searchMode);
            }
            if (resultCallback != null) {
                resultCallback.accept(formatHits(raw));
            }
        } catch (UnsatisfiedLinkError e) {
            if (resultCallback != null) {
                resultCallback.accept("[Error] Native library not loaded. Build with: jni/build-jni.bat. " + e.getMessage());
            }
        } finally {
            progressScheduler.shutdownNow();
            try {
                progressScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            isRunning = false;
            releaseActiveRunner();
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            if (progressCallback != null) {
                long processed = lastProcessed.get();
                long total = lastTotal.get();
                if (total <= 0) {
                    total = Math.max(processed, 1);
                }
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), processed, total));
            }
        }
    }
}
