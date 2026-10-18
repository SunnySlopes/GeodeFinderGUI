package sunnyslopes.geodefinder;

/**
 * JNI check against the reference geode counts for seed {@code 0xDEADBEEF}, chunks ±32, Minecraft 1.18.
 */
public final class GeodeFinderSmoke {

    public static void main(String[] args) {
        long seed = 0xDEADBEEFL;
        int[] structure = GeodeFinderBridge.geodeSearch(seed, -32, -32, 32, 32, 1, 1, 0, 1, 1, 1);
        int hits = structure == null ? 0 : structure.length / 4;
        if (hits != 158) {
            System.err.println("structure hits " + hits);
            System.exit(1);
        }
        for (int i = 0; i + 3 < structure.length; i += 4) {
            if (structure[i + 2] != -1 || structure[i + 3] != 1) {
                System.err.println("bad structure row at " + i);
                System.exit(1);
            }
        }
        int[] full = GeodeFinderBridge.geodeSearch(seed, -32, -32, 32, 32, 1, 1, 0, 1, 1, 0);
        long sum = 0;
        for (int i = 0; i + 3 < full.length; i += 4) {
            if (full[i + 2] > 0) {
                sum += full[i + 2];
            }
        }
        if (sum != 5732) {
            System.err.println("budding sum " + sum);
            System.exit(1);
        }
        System.out.println("jni smoke ok hits " + hits + " budding " + sum);
    }

    private GeodeFinderSmoke() {}
}
