import java.util.*;

public class Bench {
    static final int WARMUP = 3, REPS = 5;
    static final long SEED = 42L;

    static int find(int[] p, int x) { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; }

    static boolean validForest(int n, int[][] e, Map<String, Object> r) {
        int[] ids = (int[]) r.get("edge_ids");
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        long sum = 0;
        boolean[] used = new boolean[e.length];
        for (int id : ids) {
            if (used[id]) return false;
            used[id] = true;
            int a = find(p, e[id][0]), b = find(p, e[id][1]);
            if (a == b) return false;
            p[a] = b; sum += e[id][2];
        }
        int c = 0;
        int[] q = new int[n];
        for (int i = 0; i < n; i++) q[i] = i;
        for (int[] x : e) q[find(q, x[0])] = find(q, x[1]);
        for (int i = 0; i < n; i++) if (find(q, i) == i) c++;
        return sum == ((Number) r.get("cost")).longValue() && ids.length == n - c && ((Number) r.get("components")).intValue() == c;
    }

    static long median(long[] a) { long[] b = a.clone(); Arrays.sort(b); return b[b.length / 2]; }

    static String fmt(long[] a) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) s.append(" / "); s.append(String.format(Locale.ROOT, "%.2f", a[i] / 1e6)); }
        return s.toString();
    }

    public static void main(String[] args) {
        System.out.println("java.version=" + System.getProperty("java.version") + " vm=" + System.getProperty("java.vm.name"));
        System.out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " arch=" + System.getProperty("os.arch")
                + " cpus=" + Runtime.getRuntime().availableProcessors() + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / (1 << 20));
        System.out.println("timer=System.nanoTime warmup=" + WARMUP + " reps=" + REPS + " seed=" + SEED + " (times in ms)");
        System.out.println();
        System.out.println("| n | m | Kruskal runs (ms) | Kruskal median | Prim runs (ms) | Prim median | cost |");
        System.out.println("|---|---|---|---|---|---|---|");
        int[][] sizes = {{10_000, 25_000}, {50_000, 125_000}, {100_000, 250_000}};
        for (int[] sz : sizes) {
            int n = sz[0], m = sz[1];
            Random rnd = new Random(SEED + n);
            int[][] e = new int[m][];
            for (int i = 0; i < m; i++) e[i] = new int[]{rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(2_000_001) - 1_000_000};
            Map<String, Object> k = Algorithms.kruskal(n, e), p = Algorithms.prim(n, e);
            if (!validForest(n, e, k) || !validForest(n, e, p)
                    || ((Number) k.get("cost")).longValue() != ((Number) p.get("cost")).longValue())
                throw new IllegalStateException("Kruskal and Prim disagree or return an invalid forest for n=" + n);
            for (int w = 0; w < WARMUP; w++) { Algorithms.kruskal(n, e); Algorithms.prim(n, e); }
            long[] tk = new long[REPS], tp = new long[REPS];
            for (int r = 0; r < REPS; r++) {                       // interleaved: same conditions for both methods
                long t0 = System.nanoTime(); Algorithms.kruskal(n, e); tk[r] = System.nanoTime() - t0;
                t0 = System.nanoTime(); Algorithms.prim(n, e); tp[r] = System.nanoTime() - t0;
            }
            System.out.printf(Locale.ROOT, "| %d | %d | %s | %.2f | %s | %.2f | %d |%n", n, m, fmt(tk), median(tk) / 1e6, fmt(tp), median(tp) / 1e6,
                    ((Number) k.get("cost")).longValue());
        }
    }
}
