import java.util.*;

/** Assignment 3: graph algorithms. All DFS are iterative (no call-stack recursion). */
public class Algorithms {

    private static final int ID_BITS = 20;            // m <= 250000 < 2^20
    private static final long ID_MASK = (1L << ID_BITS) - 1;
    private static final long W_SHIFT = 1_000_000_000L;

    // ---------- CSR adjacency (stores original edge ids) ----------
    private static final class G {
        final int[] start, to, id;
        G(int n, int[][] edges, boolean transpose, boolean undirected) {
            int m = edges.length;
            start = new int[n + 1];
            for (int[] e : edges) {
                int a = transpose ? e[1] : e[0], b = transpose ? e[0] : e[1];
                start[a + 1]++;
                if (undirected) start[b + 1]++;
            }
            for (int i = 0; i < n; i++) start[i + 1] += start[i];
            int total = start[n];
            to = new int[total];
            id = new int[total];
            int[] pos = Arrays.copyOf(start, n);
            for (int i = 0; i < m; i++) {
                int a = transpose ? edges[i][1] : edges[i][0];
                int b = transpose ? edges[i][0] : edges[i][1];
                to[pos[a]] = b; id[pos[a]++] = i;
                if (undirected) { to[pos[b]] = a; id[pos[b]++] = i; }
            }
        }
    }

    // ---------- DSU ----------
    private static int find(int[] p, int x) {
        int r = x;
        while (p[r] != r) r = p[r];
        while (p[x] != r) { int nx = p[x]; p[x] = r; x = nx; }
        return r;
    }

    public static Map<String, Object> dsu(int n, int[][] operations) {
        int[] p = new int[n], sz = new int[n];
        for (int i = 0; i < n; i++) { p[i] = i; sz[i] = 1; }
        int comps = n, largest = n > 0 ? 1 : 0;
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            int a = find(p, operations[i][0]), b = find(p, operations[i][1]);
            if (a != b) {
                if (sz[a] < sz[b]) { int t = a; a = b; b = t; }
                p[b] = a; sz[a] += sz[b];
                comps--;
                if (sz[a] > largest) largest = sz[a];
            }
            states[i] = new int[]{comps, largest};
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", states);
        return result;
    }

    // ---------- Kruskal ----------
    public static Map<String, Object> kruskal(int n, int[][] edges) {
        int m = edges.length;
        long[] keys = new long[m];
        for (int i = 0; i < m; i++) keys[i] = ((edges[i][2] + W_SHIFT) << ID_BITS) | i;
        Arrays.sort(keys);
        int[] p = new int[n], sz = new int[n];
        for (int i = 0; i < n; i++) { p[i] = i; sz[i] = 1; }
        long cost = 0;
        int cnt = 0;
        int[] chosen = new int[Math.max(0, n)];
        for (long k : keys) {
            int id = (int) (k & ID_MASK);
            int a = find(p, edges[id][0]), b = find(p, edges[id][1]);
            if (a == b) continue;
            if (sz[a] < sz[b]) { int t = a; a = b; b = t; }
            p[b] = a; sz[a] += sz[b];
            cost += edges[id][2];
            chosen[cnt++] = id;
        }
        return forest(cost, Arrays.copyOf(chosen, cnt), n - cnt);
    }

    // ---------- Prim (lazy heap, restarted in every component) ----------
    public static Map<String, Object> prim(int n, int[][] edges) {
        G g = new G(n, edges, false, true);
        boolean[] seen = new boolean[n];
        PriorityQueue<Long> pq = new PriorityQueue<>();
        long cost = 0;
        int comps = 0, cnt = 0;
        int[] chosen = new int[Math.max(0, n)];
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            comps++;
            seen[s] = true;
            push(pq, g, edges, s);
            while (!pq.isEmpty()) {
                int id = (int) (pq.poll() & ID_MASK);
                int u = edges[id][0], v = edges[id][1];
                if (seen[u] && seen[v]) continue;
                int nw = seen[u] ? v : u;
                seen[nw] = true;
                cost += edges[id][2];
                chosen[cnt++] = id;
                push(pq, g, edges, nw);
            }
        }
        return forest(cost, Arrays.copyOf(chosen, cnt), comps);
    }

    private static void push(PriorityQueue<Long> pq, G g, int[][] edges, int v) {
        for (int j = g.start[v]; j < g.start[v + 1]; j++) {
            int id = g.id[j];
            pq.add(((edges[id][2] + W_SHIFT) << ID_BITS) | id);
        }
    }

    private static Map<String, Object> forest(long cost, int[] ids, int comps) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cost", cost);
        r.put("edge_ids", ids);
        r.put("components", comps);
        return r;
    }

    // ---------- SCC: Kosaraju ----------
    public static Map<String, Object> scc(int n, int[][] edges) {
        G g = new G(n, edges, false, false);
        G gt = new G(n, edges, true, false);
        // pass 1: finish order on g
        int[] order = new int[n];
        int oc = 0;
        boolean[] seen = new boolean[n];
        int[] it = new int[n], stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            int sp = 0;
            stack[sp++] = s; seen[s] = true; it[s] = g.start[s];
            while (sp > 0) {
                int v = stack[sp - 1];
                if (it[v] < g.start[v + 1]) {
                    int w = g.to[it[v]++];
                    if (!seen[w]) { seen[w] = true; it[w] = g.start[w]; stack[sp++] = w; }
                } else {
                    order[oc++] = v;
                    sp--;
                }
            }
        }
        // pass 2: transpose, decreasing finish order
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int label = 0;
        for (int i = n - 1; i >= 0; i--) {
            int s = order[i];
            if (comp[s] != -1) continue;
            int sp = 0;
            stack[sp++] = s; comp[s] = label;
            while (sp > 0) {
                int v = stack[--sp];
                for (int j = gt.start[v]; j < gt.start[v + 1]; j++) {
                    int w = gt.to[j];
                    if (comp[w] == -1) { comp[w] = label; stack[sp++] = w; }
                }
            }
            label++;
        }
        // condensation: distinct cross pairs
        long base = (long) n + 1;
        long[] keys = new long[edges.length];
        int kc = 0;
        for (int[] e : edges) {
            int a = comp[e[0]], b = comp[e[1]];
            if (a != b) keys[kc++] = a * base + b;
        }
        Arrays.sort(keys, 0, kc);
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < kc; i++) {
            if (i > 0 && keys[i] == keys[i - 1]) continue;
            pairs.add(new int[]{(int) (keys[i] / base), (int) (keys[i] % base)});
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("component", comp);
        r.put("condensation", pairs.toArray(new int[0][]));
        return r;
    }

    // ---------- Topological order: DFS with three states ----------
    /** Returns a topological order, or null if a directed cycle exists. */
    private static int[] topoOrder(int n, int[][] edges) {
        G g = new G(n, edges, false, false);
        byte[] state = new byte[n]; // 0 unvisited, 1 active, 2 finished
        int[] it = new int[n], stack = new int[n], fin = new int[n];
        int fc = 0;
        for (int s = 0; s < n; s++) {
            if (state[s] != 0) continue;
            int sp = 0;
            stack[sp++] = s; state[s] = 1; it[s] = g.start[s];
            while (sp > 0) {
                int v = stack[sp - 1];
                if (it[v] < g.start[v + 1]) {
                    int w = g.to[it[v]++];
                    if (state[w] == 1) return null;       // back edge -> cycle
                    if (state[w] == 0) { state[w] = 1; it[w] = g.start[w]; stack[sp++] = w; }
                } else {
                    state[v] = 2;
                    fin[fc++] = v;
                    sp--;
                }
            }
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = fin[n - 1 - i];
        return order;
    }

    public static Map<String, Object> topo(int n, int[][] edges) {
        int[] order = topoOrder(n, edges);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("acyclic", order != null);
        r.put("order", order != null ? order : new int[0]);
        return r;
    }

    // ---------- DAG shortest paths ----------
    public static Map<String, Object> dag(int n, int source, int[][] edges) {
        int[] order = topoOrder(n, edges);   // checks the whole graph, not only reachable part
        Map<String, Object> r = new LinkedHashMap<>();
        if (order == null) {
            r.put("acyclic", false);
            r.put("distance", new Long[0]);
            r.put("parent_edge", new int[0]);
            return r;
        }
        G g = new G(n, edges, false, false);
        long[] dist = new long[n];
        boolean[] reach = new boolean[n];
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        reach[source] = true;
        for (int v : order) {
            if (!reach[v]) continue;             // never relax from unreachable vertices
            for (int j = g.start[v]; j < g.start[v + 1]; j++) {
                int w = g.to[j], id = g.id[j];
                long nd = dist[v] + edges[id][2];
                if (!reach[w] || nd < dist[w]) {
                    reach[w] = true; dist[w] = nd; parent[w] = id;
                }
            }
        }
        Long[] distance = new Long[n];
        for (int i = 0; i < n; i++) distance[i] = reach[i] ? dist[i] : null;
        r.put("acyclic", true);
        r.put("distance", distance);
        r.put("parent_edge", parent);
        return r;
    }

    /**
     * Route helper. Returns the vertex sequence source -> target built from parent_edge ids.
     * The route to the source itself is [source]. For an unreachable target it returns
     * an EMPTY list (documented no-route result).
     */
    public static List<Integer> reconstructPath(int source, int target, int[][] edges, int[] parentEdge) {
        List<Integer> path = new ArrayList<>();
        if (target == source) { path.add(source); return path; }
        if (parentEdge[target] == -1) return path;
        int cur = target;
        while (cur != source) {
            path.add(cur);
            int e = parentEdge[cur];
            if (e == -1) return new ArrayList<>();   // defensive: broken chain
            cur = edges[e][0];
        }
        path.add(source);
        Collections.reverse(path);                   // built backwards, reversed once: O(L)
        return path;
    }

    // =====================================================================
    // Adapters for Main.java: Main calls Algorithms.xxx(Map<String,Object> request).
    // They read n / edges / operations / source from the request, call the methods above
    // and return plain JSON-friendly values (List / Long / Boolean / null).
    // =====================================================================
    private static int toInt(Object o) { return ((Number) o).intValue(); }

    private static int[][] toMatrix(Object o) {
        List<?> rows = (List<?>) o;
        int[][] r = new int[rows.size()][];
        for (int i = 0; i < r.length; i++) {
            List<?> row = (List<?>) rows.get(i);
            r[i] = new int[row.size()];
            for (int j = 0; j < r[i].length; j++) r[i][j] = ((Number) row.get(j)).intValue();
        }
        return r;
    }

    private static Object plain(Object o) {
        if (o instanceof int[] a) { List<Object> l = new ArrayList<>(a.length); for (int x : a) l.add((long) x); return l; }
        if (o instanceof int[][] a) { List<Object> l = new ArrayList<>(a.length); for (int[] x : a) l.add(plain(x)); return l; }
        if (o instanceof Long[] a) { List<Object> l = new ArrayList<>(a.length); for (Long x : a) l.add(x); return l; }
        if (o instanceof Integer i) return (long) i;
        return o;
    }

    private static Map<String, Object> plain(Map<String, Object> m) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) r.put(e.getKey(), plain(e.getValue()));
        return r;
    }

    public static Map<String, Object> dsu(Map<String, Object> request) {
        return plain(dsu(toInt(request.get("n")), toMatrix(request.get("operations"))));
    }

    public static Map<String, Object> kruskal(Map<String, Object> request) {
        return plain(kruskal(toInt(request.get("n")), toMatrix(request.get("edges"))));
    }

    public static Map<String, Object> prim(Map<String, Object> request) {
        return plain(prim(toInt(request.get("n")), toMatrix(request.get("edges"))));
    }

    public static Map<String, Object> scc(Map<String, Object> request) {
        return plain(scc(toInt(request.get("n")), toMatrix(request.get("edges"))));
    }

    public static Map<String, Object> topo(Map<String, Object> request) {
        return plain(topo(toInt(request.get("n")), toMatrix(request.get("edges"))));
    }

    public static Map<String, Object> dag(Map<String, Object> request) {
        return plain(dag(toInt(request.get("n")), toInt(request.get("source")), toMatrix(request.get("edges"))));
    }
}
