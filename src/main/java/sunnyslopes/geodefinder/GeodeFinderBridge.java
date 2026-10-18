package sunnyslopes.geodefinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JNI bridge to the amethyst geode search library.
 */
public final class GeodeFinderBridge {

    private static final String LIB_BASE = "libGeodeFinderLibJ";
    private static boolean loaded = false;

    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        if (loaded) {
            return;
        }
        try {
            System.loadLibrary(LIB_BASE);
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError firstError) {
            if (tryLoadFromBundledResources()) {
                loaded = true;
                return;
            }
            Path base = Paths.get(System.getProperty("user.dir", "."));
            List<String> libNames = nativeLibNames();
            Path[] candidates = {
                base.resolve("native").resolve("windows"),
                base.resolve("native").resolve("linux"),
                base.resolve("native").resolve("macos"),
                base.resolve("native"),
                base.resolve("jni").resolve("native").resolve("jni").resolve("build").resolve("native"),
                base.resolve("build").resolve("libs").resolve("native"),
            };
            for (Path dir : candidates) {
                for (String libName : libNames) {
                    File file = dir.resolve(libName).toFile();
                    if (file.exists()) {
                        System.load(file.getAbsolutePath());
                        loaded = true;
                        return;
                    }
                }
            }
            throw new UnsatisfiedLinkError("GeodeFinder native library not found. Build with: jni/build-jni.bat. "
                + firstError.getMessage());
        }
    }

    private static boolean tryLoadFromBundledResources() {
        String osFolder = osResourceFolder();
        if (osFolder == null) {
            return false;
        }
        for (String libName : nativeLibNames()) {
            if (tryLoadSingleBundledResource("/native/" + osFolder + "/" + libName, libName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryLoadSingleBundledResource(String resourcePath, String libName) {
        try (InputStream input = GeodeFinderBridge.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }
            Path tempDir = Files.createTempDirectory("geodefinder-native-");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(input, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            System.load(tempLib.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static String osResourceFolder() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "macos";
        }
        if (os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("linux")) {
            return "linux";
        }
        return null;
    }

    private static List<String> nativeLibNames() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> names = new ArrayList<>();
        if (os.contains("win")) {
            names.add(LIB_BASE + ".dll");
            names.add("GeodeFinderLibJ.dll");
        } else if (os.contains("mac")) {
            names.add(LIB_BASE + ".dylib");
        } else {
            names.add(LIB_BASE + ".so");
        }
        return names;
    }

    /**
     * @param version 0 = 1.17.x, 1 = 1.18.0~1.18.1, 2 = 1.18.2~1.19.3, 3 = 1.19.4~26.2, 4 = 26.3+
     * @param searchMode 0 = full, 1 = structure count only, 2 = fast hotspot search
     * @return repeating {@code blockX, blockZ, budding, geodeCount}; budding is -1 for structure-only
     */
    public static native int[] geodeSearch(long seed, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                            int geodeThreshold, int buddingThreshold, int loadedRadius, int numThreads,
                                            int version, int searchMode);

    /**
     * @return {@code [current, total, phase, status, tryPause, tryStop]}, or null when idle.
     *         phase 1 = geode scan, phase 2 = budding. status 1 = paused.
     */
    public static native int[] getSearchProgress();

    /** Hits finished so far in phase 2. Same layout as {@link #geodeSearch}. */
    public static native int[] getNowResult();

    public static native boolean pause();

    public static native boolean resume();

    public static native boolean stop();

    private GeodeFinderBridge() {}
}
