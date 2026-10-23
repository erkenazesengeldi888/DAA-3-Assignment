import java.nio.file.*;
import java.util.*;

public class TestRunner {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    static final class P {
        final String s; int i = 0;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        String str() {
            i++; StringBuilder b = new StringBuilder();
            while (s.charAt(i) != '"') { char c = s.charAt(i++); if (c == '\\') b.append(s.charAt(i++)); else b.append(c); }
            i++; return b.toString();
        }
        Object val() {
            ws(); char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = str(); ws(); i++; m.put(k, val()); ws(); if (s.charAt(i++) == '}') return m; }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(val()); ws(); if (s.charAt(i++) == ']') return l; }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int j = i;
            while (i < s.length() && "+-0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Long.parseLong(s.substring(j, i));
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String line) { return (Map<String, Object>) new P(line).val(); }

    static int I(Object o) { return ((Number) o).intValue(); }
    static long num(Object o) { return ((Number) o).longValue(); }

    static int[] a1(Object o) {
        List<?> l = (List<?>) o; int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = I(l.get(i));
        return r;
    }

    static int[][] a2(Object o) {
        List<?> l = (List<?>) o; int[][] r = new int[l.size()][];
        for (int i = 0; i < r.length; i++) r[i] = a1(l.get(i));
        return r;
    }

    static Long[] longs(Object o) {
        List<?> l = (List<?>) o; Long[] r = new Long[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i) == null ? null : num(l.get(i));
        return r;
    }

    static String js(Object o) {
        if (o == null) return "null";
        StringBuilder b = new StringBuilder();
        if (o instanceof int[] a) {
            b.append('['); for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); } return b.append(']').toString();
        }
        if (o instanceof int[][] a) {
            b.append('['); for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(js(a[i])); } return b.append(']').toString();
        }
        if (o instanceof Long[] a) {
            b.append('['); for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(js(a[i])); } return b.append(']').toString();
        }
        if (o instanceof Map<?, ?> m) {
            b.append('{'); boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(','); first = false;
                b.append('"').append(e.getKey()).append("\":").append(js(e.getValue()));
            }
            return b.append('}').toString();
        }
        return o.toString();
    }

    static Map<String, Object> run(Map<String, Object> q) {
        String t = (String) q.get("task");
        int n = I(q.get("n"));
        switch (t) {
            case "dsu": return Algorithms.dsu(n, a2(q.get("operations")));
            case "kruskal": return Algorithms.kruskal(n, a2(q.get("edges")));
            case "prim": return Algorithms.prim(n, a2(q.get("edges")));
            case "scc": return Algorithms.scc(n, a2(q.get("edges")));
            case "topo": return Algorithms.topo(n, a2(q.get("edges")));
            case "dag": return Algorithms.dag(n, I(q.get("source")), a2(q.get("edges")));
            default: throw new IllegalArgumentException(t);
        }
    }

    static int f(int[] p, int x) { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; }

    static boolean forestOk(int n, int[][] e, int[] ids, long cost, int comps, long expCost, int expComps) {
        if (cost != expCost || comps != expComps || ids.length != n - expComps) return false;
        boolean[] used = new boolean[e.length];
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        long sum = 0;
        for (int id : ids) {
            if (id < 0 || id >= e.length || used[id]) return false;
            used[id] = true;
            int a = f(p, e[id][0]), b = f(p, e[id][1]);
            if (a == b) return false;           // loop or cycle
            p[a] = b; sum += e[id][2];
        }
        return sum == expCost;
    }

    static int[] canonFromGroups(int n, Object groups) {
        int[] canon = new int[n];
        Arrays.fill(canon, -1);
        for (Object g : (List<?>) groups) {
            int[] m = a1(g); int mn = Integer.MAX_VALUE;
            for (int v : m) mn = Math.min(mn, v);
            for (int v : m) canon[v] = mn;
        }
        return canon;
    }

    static Set<Long> condSet(int n, int[][] pairs) {
        Set<Long> s = new HashSet<>();
        for (int[] p : pairs) s.add(p[0] * (n + 1L) + p[1]);
        return s;
    }

    static boolean sccOk(int n, int[] comp, int[][] cond, int[] canonExp, Set<Long> condExp) {
        if (comp.length != n) return false;
        Map<Integer, Integer> rep = new HashMap<>();
        for (int v = 0; v < n; v++) { if (comp[v] < 0) return false; rep.merge(comp[v], v, Math::min); }
        for (int v = 0; v < n; v++) if (rep.get(comp[v]) != canonExp[v]) return false;
        Set<Long> got = new HashSet<>();
        for (int[] c : cond) {
            Integer a = rep.get(c[0]), b = rep.get(c[1]);
            if (a == null || b == null || a.equals(b)) return false;
            if (!got.add(a * (n + 1L) + b)) return false;     // duplicate pair
        }
        return got.equals(condExp);
    }

    static boolean topoOk(int n, int[][] e, boolean acyclic, int[] order, boolean expAcyclic) {
        if (acyclic != expAcyclic) return false;
        if (!acyclic) return order.length == 0;
        if (order.length != n) return false;
        int[] pos = new int[n];
        Arrays.fill(pos, -1);
        for (int i = 0; i < n; i++) { if (order[i] < 0 || order[i] >= n || pos[order[i]] != -1) return false; pos[order[i]] = i; }
        for (int[] x : e) if (pos[x[0]] >= pos[x[1]]) return false;
        return true;
    }

    static boolean dagOk(int n, int src, int[][] e, boolean acyc, Long[] dist, int[] par, boolean expAcyc, Long[] expDist) {
        if (acyc != expAcyc) return false;
        if (!acyc) return dist.length == 0 && par.length == 0;
        if (dist.length != n || par.length != n) return false;
        if (expDist != null && !Arrays.equals(expDist, dist)) return false;
        if (dist[src] == null || dist[src] != 0 || par[src] != -1) return false;
        byte[] st = new byte[n];                                  // 0 unknown, 1 on current walk, 2 known to reach source
        st[src] = 2;
        int[] walk = new int[n];
        for (int v = 0; v < n; v++) {
            if (v == src) continue;
            if (dist[v] == null) { if (par[v] != -1) return false; continue; }
            int p = par[v];
            if (p < 0 || p >= e.length || e[p][1] != v) return false;
            Long du = dist[e[p][0]];
            if (du == null || du + e[p][2] != dist[v]) return false;
        }
        for (int v = 0; v < n; v++) {                             // parent chains must reach the source (memoised, O(n))
            if (dist[v] == null || st[v] == 2) continue;
            int wl = 0, cur = v;
            while (st[cur] == 0) { st[cur] = 1; walk[wl++] = cur; cur = e[par[cur]][0]; }
            if (st[cur] != 2) return false;                       // chain loops without reaching the source
            for (int i = 0; i < wl; i++) st[walk[i]] = 2;
        }
        for (int[] x : e) {
            if (dist[x[0]] == null) continue;
            if (dist[x[1]] == null || dist[x[1]] > dist[x[0]] + x[2]) return false;
        }
        return true;
    }

    static boolean verify(Map<String, Object> q, Map<String, Object> r, Map<String, Object> ex) {
        String t = (String) q.get("task");
        int n = I(q.get("n"));
        switch (t) {
            case "dsu": return js(r.get("states")).equals(js(a2(ex.get("states"))));
            case "kruskal": case "prim":
                return forestOk(n, a2(q.get("edges")), (int[]) r.get("edge_ids"), num(r.get("cost")),
                        I(r.get("components")), num(ex.get("cost")), I(ex.get("components")));
            case "scc":
                return sccOk(n, (int[]) r.get("component"), (int[][]) r.get("condensation"),
                        canonFromGroups(n, ex.get("groups")), condSet(n, a2(ex.get("condensation"))));
            case "topo":
                return topoOk(n, a2(q.get("edges")), (Boolean) r.get("acyclic"), (int[]) r.get("order"), (Boolean) ex.get("acyclic"));
            case "dag":
                return dagOk(n, I(q.get("source")), a2(q.get("edges")), (Boolean) r.get("acyclic"), (Long[]) r.get("distance"),
                        (int[]) r.get("parent_edge"), (Boolean) ex.get("acyclic"), ex.containsKey("distance") ? longs(ex.get("distance")) : null);
            default: return false;
        }
    }

    static void fileCases() throws Exception {
        List<String> in = Files.readAllLines(Paths.get("tests/my_cases.jsonl"));
        List<String> exp = Files.readAllLines(Paths.get("tests/expected.jsonl"));
        List<String> actual = new ArrayList<>();
        List<String> first = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            Map<String, Object> q = parse(in.get(i)), ex = parse(exp.get(i));
            Map<String, Object> r = run(q);
            String line = js(r);
            actual.add(line); first.add(line);
            check("case " + (i + 1) + " " + ex.get("name") + " -> " + line, verify(q, r, ex));
        }
        Files.write(Paths.get("tests/actual.jsonl"), actual);
        boolean same = true;
        for (int pass2 = 0; pass2 < 2; pass2++)
            for (int i = in.size() - 1; i >= 0; i--)
                if (!js(run(parse(in.get(i)))).equals(first.get(i))) same = false;
        check("state reset: batch repeated (reverse order, twice) in one process gives identical outputs", same);
    }

    static void witnessTests() {
        int[][] g = {{0, 1, 4}, {1, 2, 2}, {0, 2, 3}};
        check("forest: alternative valid id order [2,1] accepted", forestOk(4, g, new int[]{2, 1}, 5, 2, 5, 2));
        check("forest: cycle [0,1,2] rejected", !forestOk(4, g, new int[]{0, 1, 2}, 9, 2, 5, 2));
        check("forest: suboptimal [0,1] (cost 6) rejected", !forestOk(4, g, new int[]{0, 1}, 6, 2, 5, 2));
        check("forest: repeated id [1,1] rejected", !forestOk(4, g, new int[]{1, 1}, 4, 2, 5, 2));
        check("forest: wrong claimed cost rejected", !forestOk(4, g, new int[]{1, 2}, 4, 2, 5, 2));
        int[][] loop = {{0, 0, -5}, {0, 1, 3}};
        check("forest: self-loop id rejected", !forestOk(2, loop, new int[]{0}, -5, 1, 3, 1));

        int[][] te = {{0, 2}, {1, 2}, {2, 3}};
        check("topo: alternative order [0,1,2,3] accepted", topoOk(4, te, true, new int[]{0, 1, 2, 3}, true));
        check("topo: order violating an edge rejected", !topoOk(4, te, true, new int[]{2, 0, 1, 3}, true));
        check("topo: missing vertex rejected", !topoOk(4, te, true, new int[]{0, 1, 2}, true));
        check("topo: repeated vertex rejected", !topoOk(4, te, true, new int[]{0, 0, 2, 3}, true));
        check("topo: acyclic=true on cyclic graph rejected", !topoOk(2, new int[][]{{0, 1}, {1, 0}}, true, new int[]{0, 1}, false));

        int n = 4; int[] canon = {0, 0, 2, 3}; Set<Long> ce = condSet(n, new int[][]{{0, 2}, {2, 3}});
        check("scc: permuted labels [9,9,3,1] accepted", sccOk(n, new int[]{9, 9, 3, 1}, new int[][]{{9, 3}, {3, 1}}, canon, ce));
        check("scc: merged components rejected", !sccOk(n, new int[]{0, 0, 0, 3}, new int[][]{{0, 3}}, canon, ce));
        check("scc: split component rejected", !sccOk(n, new int[]{0, 1, 2, 3}, new int[][]{{0, 2}, {1, 2}, {2, 3}}, canon, ce));
        check("scc: duplicate condensation pair rejected", !sccOk(n, new int[]{9, 9, 3, 1}, new int[][]{{9, 3}, {9, 3}, {3, 1}}, canon, ce));
        check("scc: missing condensation pair rejected", !sccOk(n, new int[]{9, 9, 3, 1}, new int[][]{{9, 3}}, canon, ce));

        int[][] de = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        Long[] d = {0L, 3L, -4L, 0L, null};
        check("dag: correct answer accepted", dagOk(5, 0, de, true, d, new int[]{-1, 0, 2, 3, -1}, true, d));
        check("dag: parent edge that does not end at vertex rejected", !dagOk(5, 0, de, true, d, new int[]{-1, 0, 3, 3, -1}, true, d));
        check("dag: non-tight parent (edge 1 gives 8, not -4) rejected", !dagOk(5, 0, de, true, d, new int[]{-1, 0, 1, 3, -1}, true, d));
        check("dag: wrong distance rejected", !dagOk(5, 0, de, true, new Long[]{0L, 3L, -4L, 1L, null}, new int[]{-1, 0, 2, 3, -1}, true, d));
        check("dag: reachable vertex reported unreachable rejected", !dagOk(5, 0, de, true, new Long[]{0L, 3L, -4L, null, null}, new int[]{-1, 0, 2, -1, -1}, true, null));
        int[][] tie = {{0, 1, 5}, {0, 1, 5}, {1, 2, 0}, {0, 2, 5}};
        Long[] td = {0L, 5L, 5L};
        check("dag: tie alternative A (parents 1,2) accepted", dagOk(3, 0, tie, true, td, new int[]{-1, 1, 2}, true, td));
        check("dag: tie alternative B (parents 0,3) accepted", dagOk(3, 0, tie, true, td, new int[]{-1, 0, 3}, true, td));
        check("dag: acyclic=false on cyclic graph requires empty arrays", !dagOk(3, 0, tie, false, td, new int[]{-1, 0, 3}, false, null));
    }

    static boolean routeOk(int src, int tgt, int[][] e, int[] par, Long[] dist, List<Integer> path, StringBuilder log) {
        if (dist[tgt] == null) { log.append(" path=").append(path); return path.isEmpty(); }
        if (path.isEmpty() || path.get(0) != src || path.get(path.size() - 1) != tgt) return false;
        long cost = 0; List<Integer> ids = new ArrayList<>();
        for (int k = 1; k < path.size(); k++) {
            int id = par[path.get(k)];
            if (e[id][0] != path.get(k - 1) || e[id][1] != path.get(k)) return false;   // direction
            cost += e[id][2]; ids.add(id);
        }
        log.append(" path=").append(path).append(" edge_ids=").append(ids).append(" cost=").append(cost);
        return cost == dist[tgt];
    }

    @SuppressWarnings("unchecked")
    static void routeTests() {
        int[][] e = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        Map<String, Object> r = Algorithms.dag(5, 0, e);
        Long[] d = (Long[]) r.get("distance"); int[] par = (int[]) r.get("parent_edge");
        int[] targets = {3, 0, 4};
        String[] what = {"reachable target 3", "source 0", "unreachable target 4"};
        for (int k = 0; k < 3; k++) {
            StringBuilder log = new StringBuilder();
            List<Integer> p = Algorithms.reconstructPath(0, targets[k], e, par);
            boolean ok = routeOk(0, targets[k], e, par, d, p, log);
            check("route(example): " + what[k] + log, ok);
        }
        check("route(example): path to 3 is exactly [0,1,2,3]", Algorithms.reconstructPath(0, 3, e, par).equals(List.of(0, 1, 2, 3)));
        check("route(example): path to source is [source]", Algorithms.reconstructPath(0, 0, e, par).equals(List.of(0)));
        check("route(example): unreachable target gives empty list", Algorithms.reconstructPath(0, 4, e, par).isEmpty());

        int[][] tie = {{0, 1, 5}, {0, 1, 5}, {1, 2, 0}, {0, 2, 5}};
        r = Algorithms.dag(3, 0, tie);
        d = (Long[]) r.get("distance"); par = (int[]) r.get("parent_edge");
        for (int t = 0; t < 3; t++) {
            StringBuilder log = new StringBuilder(" parent_edge=" + Arrays.toString(par));
            List<Integer> p = Algorithms.reconstructPath(0, t, tie, par);
            boolean ok = routeOk(0, t, tie, par, d, p, log);
            check("route(tied/parallel): target " + t + log, ok);
        }
        int[][] mid = {{0, 1, 1}, {1, 2, 1}, {2, 3, 4}};
        r = Algorithms.dag(4, 2, mid);
        d = (Long[]) r.get("distance"); par = (int[]) r.get("parent_edge");
        check("route(source 2): to 3 = [2,3]; to 0 = []", Algorithms.reconstructPath(2, 3, mid, par).equals(List.of(2, 3))
                && Algorithms.reconstructPath(2, 0, mid, par).isEmpty());
    }

    static void randomSmall() {
        Random rnd = new Random(20261022L);
        int trials = 3000, bad = 0, cyclic = 0;
        for (int t = 0; t < trials; t++) {
            int n = 1 + rnd.nextInt(6), m = rnd.nextInt(10);
            int[][] ew = new int[m][], eu = new int[m][];
            for (int i = 0; i < m; i++) {
                ew[i] = new int[]{rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(11) - 5};
                eu[i] = new int[]{ew[i][0], ew[i][1]};
            }
            boolean[][] r = new boolean[n][n];
            for (int[] x : eu) r[x[0]][x[1]] = true;
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (r[i][k] && r[k][j]) r[i][j] = true;
            boolean cyc = false;
            for (int v = 0; v < n; v++) if (r[v][v]) cyc = true;
            if (cyc) cyclic++;
            int[] p = new int[n];
            for (int i = 0; i < n; i++) p[i] = i;
            for (int[] x : eu) p[f(p, x[0])] = f(p, x[1]);
            int c = 0;
            for (int i = 0; i < n; i++) if (f(p, i) == i) c++;
            long best = Long.MAX_VALUE;
            for (int mask = 0; mask < (1 << m); mask++) {
                int[] q = new int[n];
                for (int i = 0; i < n; i++) q[i] = i;
                long s = 0; int k = 0; boolean ok = true;
                for (int i = 0; i < m && ok; i++) if ((mask >> i & 1) != 0) {
                    int a = f(q, ew[i][0]), b = f(q, ew[i][1]);
                    if (a == b) ok = false; else { q[a] = b; s += ew[i][2]; k++; }
                }
                if (ok && k == n - c) best = Math.min(best, s);
            }
            for (String algo : new String[]{"kruskal", "prim"}) {
                Map<String, Object> res = algo.equals("kruskal") ? Algorithms.kruskal(n, ew) : Algorithms.prim(n, ew);
                if (!forestOk(n, ew, (int[]) res.get("edge_ids"), num(res.get("cost")), I(res.get("components")), best, c)) bad++;
            }

            int[] canon = new int[n];
            for (int v = 0; v < n; v++) { canon[v] = v; for (int u = 0; u < v; u++) if (r[v][u] && r[u][v]) { canon[v] = u; break; } }
            Set<Long> ce = new HashSet<>();
            for (int[] x : eu) if (canon[x[0]] != canon[x[1]]) ce.add(canon[x[0]] * (n + 1L) + canon[x[1]]);
            Map<String, Object> sc = Algorithms.scc(n, eu);
            if (!sccOk(n, (int[]) sc.get("component"), (int[][]) sc.get("condensation"), canon, ce)) bad++;
            // topo oracle
            Map<String, Object> tp = Algorithms.topo(n, eu);
            if (!topoOk(n, eu, (Boolean) tp.get("acyclic"), (int[]) tp.get("order"), !cyc)) bad++;
            // DAG oracle: Bellman-Ford on acyclic graphs
            int src = rnd.nextInt(n);
            Long[] expD = null;
            if (!cyc) {
                expD = new Long[n]; expD[src] = 0L;
                for (int round = 0; round < n; round++)
                    for (int[] x : ew) if (expD[x[0]] != null && (expD[x[1]] == null || expD[x[1]] > expD[x[0]] + x[2])) expD[x[1]] = expD[x[0]] + x[2];
            }
            Map<String, Object> dg = Algorithms.dag(n, src, ew);
            if (!dagOk(n, src, ew, (Boolean) dg.get("acyclic"), (Long[]) dg.get("distance"), (int[]) dg.get("parent_edge"), !cyc, expD)) bad++;
        }
        check("random small graphs: " + trials + " trials (" + cyclic + " cyclic) x 5 algorithms vs brute force / Floyd-Warshall / Bellman-Ford, failures=" + bad, bad == 0);
    }

    static long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

    static void dsuLarge(String name, int n, int[][] ops) {
        long t0 = System.nanoTime();
        int[][] st = (int[][]) Algorithms.dsu(n, ops).get("states");
        long el = ms(t0);
        int[] p = new int[n], sz = new int[n];
        for (int i = 0; i < n; i++) { p[i] = i; sz[i] = 1; }
        int comps = n, largest = n > 0 ? 1 : 0; boolean ok = st.length == ops.length;
        for (int i = 0; ok && i < ops.length; i++) {          // independent: union by size, no path compression
            int a = ops[i][0], b = ops[i][1];
            while (p[a] != a) a = p[a];
            while (p[b] != b) b = p[b];
            if (a != b) { if (sz[a] < sz[b]) { int x = a; a = b; b = x; } p[b] = a; sz[a] += sz[b]; comps--; largest = Math.max(largest, sz[a]); }
            if (st[i][0] != comps || st[i][1] != largest) ok = false;
        }
        check("large dsu " + name + " n=" + n + " ops=" + ops.length + " (" + el + " ms)", ok);
    }

    static int independentComponents(int n, int[][] e) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        for (int[] x : e) p[f(p, x[0])] = f(p, x[1]);
        int c = 0;
        for (int i = 0; i < n; i++) if (f(p, i) == i) c++;
        return c;
    }

    static void msfLarge(String name, int n, int[][] e) {
        long t0 = System.nanoTime();
        Map<String, Object> k = Algorithms.kruskal(n, e);
        long tk = ms(t0); t0 = System.nanoTime();
        Map<String, Object> pr = Algorithms.prim(n, e);
        long tp = ms(t0);
        int c = independentComponents(n, e);
        long cost = num(k.get("cost"));
        boolean ok = forestOk(n, e, (int[]) k.get("edge_ids"), cost, I(k.get("components")), cost, c)
                && forestOk(n, e, (int[]) pr.get("edge_ids"), num(pr.get("cost")), I(pr.get("components")), cost, c);
        check("large msf " + name + " n=" + n + " m=" + e.length + " cost=" + cost + " comps=" + c + " (kruskal " + tk + " ms, prim " + tp + " ms)", ok);
    }

    static void largeTests() {
        int N = 100000;
        Random rnd = new Random(1);
        int[][] ops = new int[250000][];
        for (int i = 0; i < ops.length; i++) ops[i] = new int[]{rnd.nextInt(N), rnd.nextInt(N)};
        dsuLarge("random", N, ops);
        int[][] chain = new int[N - 1][];
        for (int i = 0; i < N - 1; i++) chain[i] = new int[]{i + 1, i};
        dsuLarge("chain", N, chain);
        int[][] star = new int[250000][];
        for (int i = 0; i < star.length; i++) star[i] = new int[]{0, i % N};
        dsuLarge("star with repeats", N, star);

        int[][] e = new int[250000][];
        for (int i = 0; i < e.length; i++) e[i] = new int[]{rnd.nextInt(N), rnd.nextInt(N), rnd.nextInt(2_000_000_001) - 1_000_000_000};
        msfLarge("random weights", N, e);
        int[][] ex = new int[250000][];
        for (int i = 0; i < ex.length; i++) ex[i] = new int[]{rnd.nextInt(N), rnd.nextInt(N), rnd.nextBoolean() ? 1_000_000_000 : -1_000_000_000};
        msfLarge("extreme weights", N, ex);
        int[][] sp = new int[30000][];
        for (int i = 0; i < sp.length; i++) sp[i] = new int[]{rnd.nextInt(N), rnd.nextInt(N), rnd.nextInt(2001) - 1000};
        msfLarge("sparse with many isolates", N, sp);
        int[][] pc = new int[N - 1][];
        for (int i = 0; i < N - 1; i++) pc[i] = new int[]{i, i + 1, -1_000_000_000};
        msfLarge("path of -1e9 (cost -99999e9)", N, pc);

        // deep chains for topo / scc / dag
        int[][] fw = new int[N - 1][], bw = new int[N - 1][], w = new int[N - 1][];
        for (int i = 0; i < N - 1; i++) { fw[i] = new int[]{i, i + 1}; bw[i] = new int[]{i + 1, i}; w[i] = new int[]{i, i + 1, -1_000_000_000}; }
        long t0 = System.nanoTime();
        int[] ord = (int[]) Algorithms.topo(N, fw).get("order");
        boolean ok = ord.length == N;
        for (int i = 0; ok && i < N; i++) if (ord[i] != i) ok = false;
        check("deep chain topo forward n=" + N + " order is exactly 0..n-1 (" + ms(t0) + " ms)", ok);
        ord = (int[]) Algorithms.topo(N, bw).get("order");
        ok = ord.length == N;
        for (int i = 0; ok && i < N; i++) if (ord[i] != N - 1 - i) ok = false;
        check("deep chain topo backward n=" + N + " order is exactly n-1..0", ok);
        int[][] closed = Arrays.copyOf(fw, N);
        closed[N - 1] = new int[]{N - 1, 0};
        Map<String, Object> tc = Algorithms.topo(N, closed);
        check("deep cycle of n=" + N + " topo -> acyclic=false, order empty", topoOk(N, closed, (Boolean) tc.get("acyclic"), (int[]) tc.get("order"), false));
        t0 = System.nanoTime();
        Map<String, Object> sc = Algorithms.scc(N, fw);
        int[] cl = (int[]) sc.get("component"), canon = new int[N];
        for (int i = 0; i < N; i++) canon[i] = i;
        Set<Long> ce = new HashSet<>();
        for (int[] x : fw) ce.add(x[0] * (N + 1L) + x[1]);
        check("deep chain scc: n singletons, n-1 condensation pairs (" + ms(t0) + " ms)", sccOk(N, cl, (int[][]) sc.get("condensation"), canon, ce));
        sc = Algorithms.scc(N, closed);
        Arrays.fill(canon, 0);
        check("deep cycle scc: one component, no condensation pairs", sccOk(N, (int[]) sc.get("component"), (int[][]) sc.get("condensation"), canon, new HashSet<>()));
        t0 = System.nanoTime();
        Map<String, Object> dg = Algorithms.dag(N, 0, w);
        Long[] dist = (Long[]) dg.get("distance");
        int[] par = (int[]) dg.get("parent_edge");
        Long[] expD = new Long[N];
        for (int i = 0; i < N; i++) expD[i] = -1_000_000_000L * i;
        check("deep chain dag n=" + N + " distance[i] = -i*1e9 (beyond 32 bits) (" + ms(t0) + " ms)", dagOk(N, 0, w, true, dist, par, true, expD));
        List<Integer> path = Algorithms.reconstructPath(0, N - 1, w, par);
        boolean pathOk = path.size() == N;
        for (int i = 0; pathOk && i < N; i++) if (path.get(i) != i) pathOk = false;
        check("route helper on deep chain: path 0..n-1 of " + N + " vertices", pathOk);

        int B = 1000, S = 100;
        int[] perm = new int[N];
        for (int i = 0; i < N; i++) perm[i] = i;
        for (int i = N - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int x = perm[i]; perm[i] = perm[j]; perm[j] = x; }
        int[][] pe = new int[250000][];
        int k = 0;
        for (int b = 0; b < B; b++) for (int j = 0; j < S; j++) pe[k++] = new int[]{perm[b * S + j], perm[b * S + (j + 1) % S]};
        for (int i = 0; i < 50000; i++) { int b = rnd.nextInt(B); pe[k++] = new int[]{perm[b * S + rnd.nextInt(S)], perm[b * S + rnd.nextInt(S)]}; }
        for (int i = 0; i < 100000; i++) {
            int b1 = rnd.nextInt(B - 1), b2 = b1 + 1 + rnd.nextInt(B - 1 - b1);
            pe[k++] = new int[]{perm[b1 * S + rnd.nextInt(S)], perm[b2 * S + rnd.nextInt(S)]};
        }
        int[] blockOf = new int[N], blockMin = new int[B];
        Arrays.fill(blockMin, Integer.MAX_VALUE);
        for (int i = 0; i < N; i++) { blockOf[perm[i]] = i / S; blockMin[i / S] = Math.min(blockMin[i / S], perm[i]); }
        int[] pcanon = new int[N];
        for (int v = 0; v < N; v++) pcanon[v] = blockMin[blockOf[v]];
        Set<Long> pce = new HashSet<>();
        for (int[] x : pe) if (blockOf[x[0]] != blockOf[x[1]]) pce.add(blockMin[blockOf[x[0]]] * (N + 1L) + blockMin[blockOf[x[1]]]);
        t0 = System.nanoTime();
        sc = Algorithms.scc(N, pe);
        long el = ms(t0);
        check("planted scc n=" + N + " m=" + pe.length + " 1000 components, " + pce.size() + " distinct condensation pairs (" + el + " ms)",
                sccOk(N, (int[]) sc.get("component"), (int[][]) sc.get("condensation"), pcanon, pce));
        Map<String, Object> tpl = Algorithms.topo(N, pe);
        check("planted graph is cyclic: topo -> false", topoOk(N, pe, (Boolean) tpl.get("acyclic"), (int[]) tpl.get("order"), false));

        // random DAG at full size
        int[] pm = new int[N];
        for (int i = 0; i < N; i++) pm[i] = i;
        for (int i = N - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int x = pm[i]; pm[i] = pm[j]; pm[j] = x; }
        int[][] de = new int[250000][];
        for (int i = 0; i < de.length; i++) {            // first n-1 edges: spine pm[0]->pm[1]->..., rest random forward edges
            int a = i < N - 1 ? i : rnd.nextInt(N - 1), b = i < N - 1 ? i + 1 : a + 1 + rnd.nextInt(N - 1 - a);
            de[i] = new int[]{pm[a], pm[b], rnd.nextInt(2_000_000_001) - 1_000_000_000};
        }
        int[][] unweighted = new int[de.length][];
        for (int i = 0; i < de.length; i++) unweighted[i] = new int[]{de[i][0], de[i][1]};
        Map<String, Object> tt = Algorithms.topo(N, unweighted);
        check("random DAG n=" + N + " m=" + de.length + " topo precedence", topoOk(N, unweighted, (Boolean) tt.get("acyclic"), (int[]) tt.get("order"), true));
        t0 = System.nanoTime();
        dg = Algorithms.dag(N, pm[0], de);
        el = ms(t0);
        dist = (Long[]) dg.get("distance");
        int reach = 0;
        for (Long x : dist) if (x != null) reach++;
        check("random DAG shortest paths from first vertex of the order, reachable=" + reach + " (" + el + " ms)",
                dagOk(N, pm[0], de, (Boolean) dg.get("acyclic"), dist, (int[]) dg.get("parent_edge"), true, null));
        // cycle outside reachability at full size
        List<int[]> ce2 = new ArrayList<>();
        for (int i = 0; i < 49999; i++) ce2.add(new int[]{i, i + 1, 1});
        ce2.add(new int[]{70000, 70001, 1});
        ce2.add(new int[]{70001, 70000, 1});
        int[][] cyc = ce2.toArray(new int[0][]);
        dg = Algorithms.dag(N, 0, cyc);
        check("dag n=" + N + ": 2-cycle unreachable from source -> acyclic=false, empty arrays",
                dagOk(N, 0, cyc, (Boolean) dg.get("acyclic"), (Long[]) dg.get("distance"), (int[]) dg.get("parent_edge"), false, null));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. hand-made cases (tests/my_cases.jsonl vs tests/expected.jsonl)");
        fileCases();
        System.out.println("== 2. alternative answers accepted, invalid witnesses rejected");
        witnessTests();
        System.out.println("== 3. route helper");
        routeTests();
        System.out.println("== 4. randomised small graphs vs independent oracles");
        randomSmall();
        System.out.println("== 5. full-size inputs and deep chains");
        largeTests();
        System.out.println("SUMMARY: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
