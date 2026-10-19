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
 * Full and fast searches larger than one million by one million blocks are split into tiles
 * so the native geode mask does not have to cover the whole rectangle at once.
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
    /** Full and fast searches above this block area are tiled. Equivalent to a 1_000_000 by 1_000_000 square. */
    public static final long TILE_AREA_BLOCKS = 1_000_000L * 1_000_000L;
    /** {@link #TILE_AREA_BLOCKS} measured in chunks. Each chunk is 16 by 16 blocks. */
    static final long TILE_AREA_CHUNKS = TILE_AREA_BLOCKS / 256L;
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
        return formatHits(decode(raw));
    }

    public static String formatHits(List<int[]> hits) {
        List<int[]> sorted = new ArrayList<>(hits);
        sortHits(sorted);
        StringBuilder sb = new StringBuilder();
        for (int[] hit : sorted) {
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
    private volatile boolean stopRequested = false;
    private volatile boolean tileMode = false;
    private volatile int lastPhase = 1;

    private final Object progressLock = new Object();
    private boolean publishLive = true;
    private boolean acceptNativePartial = false;
    private long weightBefore = 0;
    private long tileWeight = 1;
    private long totalWeight = 1;
    private int keepMinX = Integer.MIN_VALUE;
    private int keepMaxX = Integer.MAX_VALUE;
    private int keepMinZ = Integer.MIN_VALUE;
    private int keepMaxZ = Integer.MAX_VALUE;
    private final List<int[]> finishedHits = new ArrayList<>();

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
        stopRequested = true;
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
        stopRequested = false;
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

        boolean tileThisSearch = needsAreaTiling(searchMode, chunkArea);
        List<SearchTile> tiles = tileThisSearch
            ? planTiles(minChunkX, maxChunkX, minChunkZ, maxChunkZ, loadedRadius, TILE_AREA_CHUNKS)
            : List.of();
        long plannedWeight = 0;
        for (SearchTile tile : tiles) {
            plannedWeight += tile.scanArea();
        }
        synchronized (progressLock) {
            tileMode = tileThisSearch && tiles.size() > 1;
            publishLive = true;
            acceptNativePartial = false;
            finishedHits.clear();
            weightBefore = 0;
            tileWeight = tiles.isEmpty() ? Math.max(1, chunkArea) : tiles.get(0).scanArea();
            totalWeight = Math.max(1, plannedWeight);
            if (!tiles.isEmpty()) {
                SearchTile first = tiles.get(0);
                keepMinX = first.keepMinX;
                keepMaxX = first.keepMaxX;
                keepMinZ = first.keepMinZ;
                keepMaxZ = first.keepMaxZ;
            }
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
                if (arr == null || arr.length < 4) {
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
                lastPhase = phase;
                if (tileMode) {
                    publishTiledProgress(phase, cur, tot, pauseSettled, tryStop, progressCallback, publishPartial,
                        resultCallback, lastPartial, lastProcessed, lastTotal);
                    return;
                }
                if (progressCallback == null) {
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
                    publishSnapshot(formatHits(GeodeFinderBridge.getNowResult()), resultCallback, lastPartial);
                }
            } catch (Throwable ignored) {
            }
        }, PROGRESS_POLL_MS, PROGRESS_POLL_MS, TimeUnit.MILLISECONDS);

        String tiledText = null;
        try {
            if (tileMode) {
                runTiles(seed, tiles, geodeThreshold, buddingThreshold, loadedRadius, threads, version, searchMode,
                    publishPartial, progressCallback, resultCallback, lastPartial);
                List<int[]> snapshot;
                synchronized (progressLock) {
                    publishLive = false;
                    acceptNativePartial = false;
                    snapshot = new ArrayList<>(finishedHits);
                }
                tiledText = formatHits(snapshot);
            } else {
                int[] raw;
                synchronized (NATIVE_LOCK) {
                    raw = GeodeFinderBridge.geodeSearch(seed, minChunkX, minChunkZ, maxChunkX, maxChunkZ,
                        geodeThreshold, buddingThreshold, loadedRadius, threads, version, searchMode);
                }
                if (resultCallback != null) {
                    resultCallback.accept(formatHits(raw));
                }
            }
        } catch (UnsatisfiedLinkError e) {
            tiledText = null;
            if (resultCallback != null) {
                resultCallback.accept("[Error] Native library not loaded. Build with: jni/build-jni.bat. " + e.getMessage());
            }
        } finally {
            synchronized (progressLock) {
                publishLive = false;
                acceptNativePartial = false;
            }
            progressScheduler.shutdownNow();
            try {
                progressScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (tiledText != null && !stopRequested) {
                lastProcessed.set(totalWeight);
                lastTotal.set(Math.max(1L, totalWeight));
            }
            isRunning = false;
            releaseActiveRunner();
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            if (tiledText != null && resultCallback != null) {
                resultCallback.accept(tiledText);
            }
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

    static boolean needsAreaTiling(int searchMode, long chunkArea) {
        return (searchMode == MODE_FULL || searchMode == MODE_FAST) && chunkArea > TILE_AREA_CHUNKS;
    }

    /**
     * One native scan of chunks {@code [scanMin, scanMax]} on a single axis.
     * Hits whose center lies in {@code [keepMin, keepMax]} are kept.
     * The native scan emits centers from {@code scanMin - radius} through {@code scanMax - radius}.
     * Chunks outside the caller's rectangle count as empty, so only the first band may keep those partial centers.
     */
    record AxisBand(int scanMin, int scanMax, int keepMin, int keepMax) {
    }

    record SearchTile(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                      int keepMinX, int keepMaxX, int keepMinZ, int keepMaxZ) {
        long scanArea() {
            return ((long) maxChunkX - minChunkX + 1L) * ((long) maxChunkZ - minChunkZ + 1L);
        }
    }

    static List<SearchTile> planTiles(int minX, int maxX, int minZ, int maxZ, int loadedRadius, long maxChunkArea) {
        int radius = Math.max(0, Math.min(64, loadedRadius));
        long cap = Math.max(1L, maxChunkArea);
        long spanX = (long) maxX - minX + 1L;
        long spanZ = (long) maxZ - minZ + 1L;
        if (minX > maxX || minZ > maxZ || spanX <= 0 || spanZ <= 0) {
            return List.of();
        }
        long[] caps = scanCaps(spanX, spanZ, radius, cap);
        List<AxisBand> xBands = planBands(minX, maxX, radius, caps[0]);
        List<AxisBand> zBands = planBands(minZ, maxZ, radius, caps[1]);
        List<SearchTile> tiles = new ArrayList<>(Math.max(1, xBands.size() * zBands.size()));
        for (AxisBand x : xBands) {
            for (AxisBand z : zBands) {
                tiles.add(new SearchTile(x.scanMin, x.scanMax, z.scanMin, z.scanMax,
                    x.keepMin, x.keepMax, z.keepMin, z.keepMax));
            }
        }
        return tiles;
    }

    /**
     * Picks per-axis scan lengths whose product stays within {@code cap}.
     * A split axis is at least one full loaded window long, so a later band does not have to grow past the cap
     * to finish its first center.
     */
    private static long[] scanCaps(long spanX, long spanZ, int radius, long cap) {
        long window = 2L * radius + 1L;
        if (spanZ > 0 && spanX <= cap / spanZ) {
            return new long[] {spanX, spanZ};
        }
        if (spanX > 0 && spanX <= cap) {
            long rows = cap / spanX;
            if (rows >= spanZ || rows >= window) {
                return new long[] {spanX, Math.min(spanZ, rows)};
            }
        }
        if (spanZ > 0 && spanZ <= cap) {
            long cols = cap / spanZ;
            if (cols >= spanX || cols >= window) {
                return new long[] {Math.min(spanX, cols), spanZ};
            }
        }
        if (window > 0 && window <= cap / window) {
            long cols = Math.min(spanX, cap / window);
            long rows = Math.min(spanZ, cap / Math.max(1L, cols));
            if (cols >= window && rows >= window) {
                return new long[] {cols, rows};
            }
            rows = Math.min(spanZ, cap / window);
            cols = Math.min(spanX, cap / Math.max(1L, rows));
            if (cols >= 1 && rows >= 1) {
                return new long[] {Math.max(1L, cols), Math.max(1L, rows)};
            }
        }
        long cols = Math.min(spanX, Math.max(1L, cap));
        long rows = Math.min(spanZ, Math.max(1L, cap / Math.max(1L, cols)));
        return new long[] {Math.max(1L, cols), Math.max(1L, rows)};
    }

    static List<AxisBand> planBands(int min, int max, int radius, long maxScanExtent) {
        List<AxisBand> bands = new ArrayList<>();
        if (min > max) {
            return bands;
        }
        int r = Math.max(0, radius);
        long extent = Math.max(1L, maxScanExtent);
        long minCenter = (long) min - r;
        long maxCenter = (long) max - r;
        long center = minCenter;
        long guardLimit = ((long) max - min + 1L) + 2L;
        long guard = 0;
        while (center <= maxCenter && guard++ < guardLimit) {
            boolean first = bands.isEmpty();
            long scanMin = first ? min : Math.max((long) min, center - r);
            long scanMax = Math.min((long) max, scanMin + extent - 1L);
            long keepMax = scanMax - r;
            if (keepMax < center) {
                scanMax = Math.min((long) max, center + r);
                keepMax = scanMax - r;
            }
            if (scanMax >= max) {
                scanMax = max;
                keepMax = maxCenter;
            }
            if (keepMax > maxCenter) {
                keepMax = maxCenter;
            }
            if (keepMax < center) {
                break;
            }
            bands.add(new AxisBand((int) scanMin, (int) scanMax, (int) center, (int) keepMax));
            center = keepMax + 1L;
        }
        return bands;
    }

    static void collectHits(List<int[]> dest, int[] raw, int keepMinX, int keepMaxX, int keepMinZ, int keepMaxZ) {
        if (raw == null) {
            return;
        }
        for (int i = 0; i + 3 < raw.length; i += 4) {
            int centerX = blockToChunk(raw[i]);
            int centerZ = blockToChunk(raw[i + 1]);
            if (centerX < keepMinX || centerX > keepMaxX || centerZ < keepMinZ || centerZ > keepMaxZ) {
                continue;
            }
            dest.add(new int[] {raw[i], raw[i + 1], raw[i + 2], raw[i + 3]});
        }
    }

    private void runTiles(long seed, List<SearchTile> tiles, int geodeThreshold, int buddingThreshold, int loadedRadius,
                          int threads, int version, int searchMode, boolean publishPartial,
                          Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback,
                          StringBuilder lastPartial)
        throws UnsatisfiedLinkError {
        long before = 0;
        for (int i = 0; i < tiles.size(); i++) {
            if (stopRequested) {
                break;
            }
            waitIfPaused(progressCallback);
            if (stopRequested) {
                break;
            }
            SearchTile tile = tiles.get(i);
            synchronized (progressLock) {
                weightBefore = before;
                tileWeight = tile.scanArea();
                keepMinX = tile.keepMinX;
                keepMaxX = tile.keepMaxX;
                keepMinZ = tile.keepMinZ;
                keepMaxZ = tile.keepMaxZ;
                acceptNativePartial = true;
            }
            int[] raw;
            synchronized (NATIVE_LOCK) {
                raw = GeodeFinderBridge.geodeSearch(seed, tile.minChunkX, tile.minChunkZ, tile.maxChunkX, tile.maxChunkZ,
                    geodeThreshold, buddingThreshold, loadedRadius, threads, version, searchMode);
            }
            boolean stopped = stopRequested || nativeStopRequested();
            synchronized (progressLock) {
                acceptNativePartial = false;
                collectHits(finishedHits, raw, tile.keepMinX, tile.keepMaxX, tile.keepMinZ, tile.keepMaxZ);
            }
            if (publishPartial && resultCallback != null) {
                List<int[]> snapshot;
                synchronized (progressLock) {
                    snapshot = new ArrayList<>(finishedHits);
                }
                publishSnapshot(formatHits(snapshot), resultCallback, lastPartial);
            }
            if (stopped) {
                break;
            }
            before += tile.scanArea();
        }
    }

    private void waitIfPaused(Consumer<ProgressInfo> progressCallback) {
        while (isPaused && !stopRequested) {
            if (progressCallback != null) {
                long processed = lastProcessedFallback();
                long total = lastTotalFallback();
                progressCallback.accept(ProgressInfo.of(lastPhase, processed, total, getElapsedMs(), 0, true, false));
            }
            try {
                Thread.sleep(PROGRESS_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
                return;
            }
        }
    }

    private long lastProcessedFallback() {
        synchronized (progressLock) {
            return weightBefore;
        }
    }

    private long lastTotalFallback() {
        synchronized (progressLock) {
            return Math.max(1, totalWeight);
        }
    }

    private void publishTiledProgress(int phase, long cur, long tot, boolean pauseSettled, boolean tryStop,
                                      Consumer<ProgressInfo> progressCallback, boolean publishPartial,
                                      Consumer<String> resultCallback, StringBuilder lastPartial,
                                      AtomicLong lastProcessed, AtomicLong lastTotal) {
        int phaseSeen = phase;
        long before;
        long tileW;
        long totalW;
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        boolean acceptPartial;
        List<int[]> base = null;
        synchronized (progressLock) {
            before = weightBefore;
            tileW = tileWeight;
            totalW = totalWeight;
            minX = keepMinX;
            maxX = keepMaxX;
            minZ = keepMinZ;
            maxZ = keepMaxZ;
            acceptPartial = publishLive && acceptNativePartial;
            if (acceptPartial && publishPartial && phaseSeen == 2 && resultCallback != null) {
                base = new ArrayList<>(finishedHits);
            }
        }
        if (totalW <= 0) {
            totalW = 1;
        }
        double nativeFrac = tot > 0 ? Math.min(1.0, cur / (double) tot) : 0;
        double tileFrac = (phaseSeen == 2 ? 0.5 : 0.0) + 0.5 * nativeFrac;
        long processed = before + (long) (tileW * tileFrac);
        if (processed > totalW) {
            processed = totalW;
        }
        long elapsed = getElapsedMs();
        double overall = processed / (double) totalW;
        long remaining = 0L;
        if (!isPaused && overall > 0 && overall < 1.0 && elapsed > 0) {
            double eta = elapsed * (1.0 - overall) / overall;
            if (eta > 0 && eta < (double) Long.MAX_VALUE) {
                remaining = (long) eta;
            }
        }
        lastProcessed.set(processed);
        lastTotal.set(totalW);
        if (progressCallback != null) {
            progressCallback.accept(ProgressInfo.of(phaseSeen, processed, totalW, elapsed, remaining, pauseSettled, tryStop));
        }
        if (base != null) {
            collectHits(base, GeodeFinderBridge.getNowResult(), minX, maxX, minZ, maxZ);
            publishSnapshot(formatHits(base), resultCallback, lastPartial);
        }
    }

    private static void publishSnapshot(String snapshot, Consumer<String> resultCallback, StringBuilder lastPartial) {
        synchronized (lastPartial) {
            if (snapshot.contentEquals(lastPartial)) {
                return;
            }
            lastPartial.setLength(0);
            lastPartial.append(snapshot);
        }
        resultCallback.accept(snapshot);
    }

    private static boolean nativeStopRequested() {
        try {
            int[] arr = GeodeFinderBridge.getSearchProgress();
            return arr != null && arr.length > 5 && arr[5] != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
