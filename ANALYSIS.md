# Assignment 3 — Graph Algorithms: Analysis

Language: **Java 17** (`submission.json`: `{"language":"Java"}`). Algorithms: `src/Java/Algorithms.java`. Tests: `tests/`.

## 1. Build, run and test commands

Run everything from the repository root.

```sh
# launcher checks (compiles the selected language itself)
python tools/run.py --setup
python tools/run.py --check                           # 36 public cases, batch repeated twice in one process
python tools/run.py --input tests/my_cases.jsonl      # my 28 cases through the real JSON adapter

# my own tests: calls the algorithms and the route helper directly
javac -d build/tests src/Java/Algorithms.java tests/TestRunner.java
java -cp build/tests TestRunner                       # PASS/FAIL per check, exit code 1 on failure
java -Xss512k -cp build/tests TestRunner              # same with a small thread stack (deep-DFS check)

# benchmark
javac -d build/tests src/Java/Algorithms.java tests/Bench.java
java -cp build/tests Bench
```

## 2. Common design

- **Edge IDs.** `edges[i]` has ID `i`. Adjacency is built as a CSR structure (`start[]`, `to[]`, `id[]`), so the original ID travels with every adjacency entry. Kruskal sorts packed `long` keys `((w + 10^9) << 20) | id` (weights are shifted to be non-negative, `id < 2^20` because `m <= 250000`), so the ID is recovered from the low bits and ties break by ID.
- **64-bit sums.** All costs and distances are `long`. The largest possible magnitude is about `10^5 · 10^9 = 10^14`, far below `2^63`.
- **State reset.** The class has no static mutable fields. Every method allocates its own arrays, so nothing survives between requests. `TestRunner` repeats the 28-case batch in reverse order twice in one process and requires identical outputs, and `tools/run.py --check` does the same with the public batch.
- **Deep DFS.** No method uses recursion. Every DFS keeps an explicit `int[] stack` (size `n`) and an `int[] it` array holding, for each vertex, the index of the next adjacency entry to examine. A suspended vertex resumes where it stopped, so adjacency lists are never rescanned and the DFS is `O(n + m)`. The tests run chains of 100000 vertices (also with `-Xss512k`), where recursion would overflow the stack.
- **Graph size limits.** `n <= 100000`, `m <= 250000`. Memory is `O(n + m)` for every method, including the returned result.

## 3. Methods

### 3.1 DSU (`dsu`)

**Steps.** `parent[i]=i`, `size[i]=1`, `components=n`, `largest=1` (0 if `n=0`). For each pair, find both roots. If different, attach the smaller tree under the larger, add sizes, decrement `components`, update `largest`. Append `[components, largest]` after *every* operation (no initial state).

**Invariant.** The `parent` array is a forest whose trees are exactly the current components. `size[r]` is correct for every root `r`. `components` equals the number of roots and `largest` equals the maximum root size.

**Correctness.** A union of two different roots merges exactly two components and the merged size is the sum, so `components` falls by one and `largest` becomes `max(largest, new size)`. Component sizes never decrease, so keeping a running maximum is enough. Path compression changes only parent pointers inside one tree, so partition and sizes are unaffected. Rank is not used; size is maintained directly, which avoids the "rank is not size" trap.

**Edge cases.** Self-union and repeated union: both roots are equal, nothing changes, the previous state is repeated. `n=0`: empty `states`. Operations are `0` in number: empty `states`.

**Complexity.** Union by size plus path compression gives `O(α(n))` amortised per operation, total `O(n + q·α(n))` time. Memory `O(n + q)` (the `states` array has `q` rows). `find` is iterative (two passes: find root, then compress).

### 3.2 Kruskal (`kruskal`)

**Steps.** Sort edge keys by `(weight, id)`. Scan in order; if `find(u) != find(v)`, union and take the edge (add weight to `cost`, record the original ID). `components = n - (number of taken edges)`.

**Invariant.** After processing a prefix of the sorted edges, the taken edges form a minimum spanning forest of the graph made of that prefix (the DSU partition equals its connected components).

**Correctness.** Cycle/cut property: the lightest edge crossing a cut of the current partition (that is, the first edge in sorted order joining two different components) belongs to some minimum spanning forest. Negative weights change nothing, since only the order matters (the shift is applied to the sort key only; the real weight is added to the cost). Self-loops always have `find(u) == find(v)`, so they are never taken. Of parallel edges, at most one is taken. A forest of `c` components over `n` vertices has exactly `n - c` edges, so the component count follows from the number taken.

**Edge cases.** `n=0` gives cost 0, no edges, 0 components. Isolated vertices count as components. Total above 32 bits uses `long`.

**Complexity.** `O(n + m log m)` time (sort dominates), `O(n + m)` memory.

### 3.3 Prim (`prim`)

**Steps.** Build the undirected adjacency (each edge appears at both endpoints, with its ID). For each unvisited vertex `s` (a new component): mark it visited, push its incident edges into a min-heap keyed by `(weight, id)`. Pop the lightest edge; if both endpoints are already visited, skip it (stale); otherwise visit the new endpoint, add the edge, and push the new vertex's incident edges. When the heap is empty the component is complete; continue with the next unvisited vertex.

**Invariant.** Within the current component, the taken edges form a minimum spanning tree of the visited vertex set, and the heap contains every edge with at least one visited endpoint that has not been popped yet.

**Correctness.** The lightest heap edge with exactly one visited endpoint is the lightest edge crossing the cut (visited, unvisited), so it is safe by the cut property. Entries with both endpoints visited would close a cycle and are discarded. The restart in every unvisited vertex produces a spanning forest: `components` is the number of restarts. Self-loops have both endpoints visited at once and are skipped.

**Edge cases.** Same as Kruskal: `n=0`, isolates (each is its own component with an empty heap), parallel edges, negative weights.

**Complexity.** The lazy heap holds up to `2m` entries: `O(n + m log m)` time, `O(n + m)` memory. Heap entries are packed `long` keys like Kruskal's.

### 3.4 Strongly connected components (`scc`, Kosaraju)

**Steps.** (1) Iterative DFS over `G` from every unvisited vertex; push a vertex to `order` when all its out-edges are finished. (2) Build the transpose `G^T` (CSR with reversed edges). (3) Scan `order` from the last to the first element; for each vertex without a label, traverse `G^T` over unlabelled vertices and give all reached vertices a new label. (4) For every edge with different labels, record the pair `a * (n+1) + b`, sort, and drop duplicates.

**Invariant.** In pass 2, when a root `r` with the largest finish time among unlabelled vertices starts, the unlabelled vertices reachable from it in `G^T` are exactly the SCC of `r`.

**Correctness.** Take the SCC `C` that contains the unlabelled vertex of the largest finish time. In the condensation DAG of `G`, `C` has no incoming edges from other unlabelled components (an edge `D → C` would force `D` to finish later). So in `G^T`, `C` has no outgoing edges to unlabelled components, and a traversal from `r` stays inside `C` and reaches all of it. Repeating this labels every SCC. Only reachability matters in pass 2, so traversal order inside it is irrelevant (a stack without iterators is enough). Condensation pairs are cross-label edges; deduplication gives each pair once; internal edges (including self-loops) are excluded because their labels are equal.

**Edge cases.** `n=0`: both arrays empty. Isolated vertices are singleton SCCs. Duplicate edges produce one pair. Labels are `0..k-1`, which fit in 64-bit integers.

**Complexity.** Two DFS passes and two CSR builds: `O(n + m)`; sorting pairs adds `O(m log m)`. (The specification allows `O(n + m)` with hashing; sorting was chosen to avoid boxed hash sets. At `m = 250000` this costs a few milliseconds in the tests.) Memory `O(n + m)`.

### 3.5 DFS topological order (`topo`)

**Steps.** State per vertex: 0 unvisited, 1 active (on the DFS stack), 2 finished. Iterative DFS from each unvisited vertex. An edge to an active vertex means a directed cycle, return `acyclic=false`, `order=[]`. An edge to a finished vertex is ignored. When all out-edges of `v` are done, mark it finished and append to `fin`. The answer is `fin` reversed.

**Invariant.** Active vertices form a path in the DFS tree (the stack), and every finished vertex has all its out-neighbours finished.

**Correctness.** If an edge `u → v` reaches an active `v`, then `v` is an ancestor of `u`, so `v ⇒ u → v` is a cycle. Conversely, if a cycle exists, the first vertex of the cycle visited is active while the rest of the cycle is explored (white-path theorem), so some edge of the cycle reaches an active vertex. In an acyclic graph, for each edge `u → v`, `v` finishes before `u` (either `v` is discovered during `u` and finishes inside it, or `v` was already finished), so the reversed finish order places `u` before `v`.

**Edge cases.** Self-loop `u → u`: `u` is active when its own edge is read, so it is a cycle. Duplicate edges do no harm. Disconnected graphs: every component is started. `n=0`: `acyclic=true`, `order=[]`.

**Complexity.** `O(n + m)` time and memory; the `it[]` pointer array guarantees that each adjacency entry is examined once.

### 3.6 DAG shortest paths (`dag`)

**Steps.** Run the topological DFS on the *whole* graph. If it finds a cycle (even one unreachable from `source`) return `acyclic=false`, `distance=[]`, `parent_edge=[]`. Otherwise: `reach[source]=true`, `dist[source]=0`. Take vertices in topological order; skip those not marked reachable (never relax from an unreachable vertex). For each outgoing edge `(v → w)` with weight `c`: if `w` is not reachable yet or `dist[v] + c < dist[w]`, set `dist[w]`, `parent_edge[w] = edge ID`, mark reachable. Output `null` for unreachable distances, `-1` for their parents and for the source's parent.

**Invariant.** When vertex `v` is taken, `dist[v]` is the shortest distance from the source to `v` (final), and `parent_edge[v]` is the ID of an edge that ends at `v` and satisfies `dist[tail] + weight = dist[v]`.

**Correctness.** Every in-edge of `v` comes from a vertex earlier in topological order, so all candidate relaxations of `v` are complete before `v` is taken (induction on the order). Negative weights are safe since no vertex is revisited. A separate `reach` flag is used instead of a sentinel, so a finite distance such as `-1` is never confused with unreachable. Strict `<` keeps the first of equal alternatives, so parallel and tied edges give one valid parent. Following `parent_edge` moves to an earlier topological vertex each time and ends at the source.

**Edge cases.** `n=1`. Source in the middle of the order: vertices before it stay unreachable. A cycle elsewhere in the graph or a self-loop gives `acyclic=false`. Sums beyond 32 bits (chain of 100000 edges of weight `-10^9` gives `-99999·10^9`).

**Complexity.** `O(n + m)` time and memory.

### 3.7 Route helper (`reconstructPath`)

`reconstructPath(source, target, edges, parentEdge)` returns the vertex sequence. If `target == source` it returns `[source]`. If `parentEdge[target] == -1` for another target, it returns an **empty list** (this is the documented no-route result). Otherwise it follows `parentEdge` from the target back to the source (taking `edges[id][0]` as the previous vertex), appends each vertex to an `ArrayList` and reverses it once, so the result is built in `O(L)` time and space for a route of `L` vertices. A defensive check returns an empty list if a chain ends before the source. The JSON `distance` still uses `null` for unreachable vertices; the empty list concerns only the helper.

## 4. Route-helper calls (actual results)

These calls are made by `TestRunner` (section 3 of its output, see `tests/results.txt`). Each route was checked for direction (every step is the stored edge going tail → head) and for total weight equal to `distance[target]`.

Graph A (statement example): `n=5`, `source=0`, `edges = [[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]`, result `distance=[0,3,-4,0,null]`, `parent_edge=[-1,0,2,3,-1]`.

| Call | Result | Edge IDs | Total weight |
|---|---|---|---|
| `reconstructPath(0, 3, edges, parent_edge)` (reachable) | `[0, 1, 2, 3]` | `[0, 2, 3]` | 3 − 7 + 4 = 0 = `distance[3]` |
| `reconstructPath(0, 0, edges, parent_edge)` (source) | `[0]` | `[]` | 0 |
| `reconstructPath(0, 4, edges, parent_edge)` (unreachable) | `[]` (no route) | — | `distance[4] = null` |

Graph B (parallel and tied edges): `n=3`, `source=0`, `edges = [[0,1,5],[0,1,5],[1,2,0],[0,2,5]]`. Result `distance=[0,5,5]`, `parent_edge=[-1,0,3]`.

| Call | Result | Edge IDs | Total weight |
|---|---|---|---|
| `reconstructPath(0, 1, ...)` | `[0, 1]` | `[0]` (the first of the two parallel edges, `5`) | 5 |
| `reconstructPath(0, 2, ...)` | `[0, 2]` | `[3]` (ties with `0→1→2` of cost `5 + 0`; strict `<` keeps the first found) | 5 |

The tests also accept the other tied choices (`parent_edge = [-1,1,2]`) because they satisfy the same conditions: each parent edge ends at its vertex, is tight, and the chain reaches the source.

## 5. Testing

Details, the table of all hand-made cases with justification, and the checking rules are in `tests/README.md`. Summary:

- 28 hand-made cases with hand-derived expected answers (`tests/my_cases.jsonl`, `tests/expected.jsonl`); actual outputs in `tests/actual.jsonl`.
- Non-unique answers are verified by properties: forest feasibility and optimum cost, exact SCC partition and exact condensation, topological precedence, and DAG distances by tight parents plus "no edge can improve". The checkers accept other valid answers and reject invalid witnesses (section 2 of `TestRunner`).
- 3000 random small graphs against brute force, Floyd–Warshall and Bellman–Ford.
- Full-size inputs (`n=100000`, `m=250000`), chains of 100000 vertices, a planted-SCC graph, mixed requests repeated in one process.

Public checks: all 36 public cases and the state-reset check pass (`python tools/run.py --check`, run on my machine).

## 6. Benchmark: Kruskal vs Prim

**Setup.** `tests/Bench.java`. For each size one graph is generated with `java.util.Random(42 + n)`: `m = 2.5 n` random edges (self-loops and parallel edges possible), integer weights in `[-10^6, 10^6]`. Both methods receive the same `int[][]` array. Before timing, both results are verified: valid forest (own DSU check, `n − c` edges, reported cost equals the sum) and equal costs.

**Timer and boundary.** `System.nanoTime()`. The timed region is exactly one call to `Algorithms.kruskal` or `Algorithms.prim` on an already built in-memory edge array. It *includes* what each algorithm needs internally (sorting for Kruskal; adjacency build and the heap for Prim) and the construction of the result map. It *excludes* compilation, graph generation, JSON parsing, input/output and result checking. The boundary is identical for both methods.

**Protocol.** 3 untimed warm-up runs of each method per size (JIT), then 5 timed runs per method, interleaved (Kruskal, Prim, Kruskal, Prim). Median of the 5 runs.

**Environment.** Windows 11, <N> logical CPUs, <X> GB RAM, Java 17, max heap <N> MB.

**Command.** `javac -d build/tests src/Java/Algorithms.java tests/Bench.java && java -cp build/tests Bench`

**Results (ms).** Measured on my machine with the command above (five timed runs per method, median in bold column "median").

| n | m | Kruskal runs (ms) | Kruskal median | Prim runs (ms) | Prim median | cost |
|---|---|---|---|---|---|---|
| 10000 | 25000 | 8.42 / 3.65 / 5.71 / 2.96 / 4.13 | 4.13 | 20.70 / 24.83 / 22.22 / 16.83 / 18.49 | 20.70 | -5289952702 |
| 50000 | 125000 | 26.33 / 21.17 / 22.69 / 19.48 / 16.37 | 21.17 | 197.77 / 180.46 / 166.04 / 150.17 / 167.78 | 167.78 | -26423268577 |
| 100000 | 250000 | 39.76 / 50.85 / 48.71 / 47.59 / 46.85 | 47.59 | 454.95 / 433.72 / 521.92 / 457.52 / 504.24 | 457.52 | -52827134919 |

**Asymptotic argument.** Both algorithms are `O(n + m log m)`. Kruskal's `m log m` comes from sorting `m` keys; Prim's from `O(m)` heap insertions and removals (up to `2m` entries in the lazy heap). So asymptotically they are the same, and a difference can only come from constants.

**Observations.** Kruskal was faster than Prim at all three sizes. The ratio of the medians (Prim / Kruskal) was about 5.0 at n = 10000, 7.9 at n = 50000 and 9.6 at n = 100000, so the gap grew with the size. When the input grew 10 times, the Kruskal median grew about 11.5 times (4.13 ms to 47.59 ms) and the Prim median about 22 times (20.70 ms to 457.52 ms). The five runs at each size show a visible spread (for example Kruskal at n = 10000: 2.96 to 8.42 ms), so the ratio at the smallest size is less reliable than at the largest. A possible explanation, which I did not test, is that Prim's lazy heap stores boxed `Long` objects and up to 2m entries, while Kruskal sorts a primitive `long[]`.

**Limitations (to check against your run).** Sizes are fixed and few; one random graph family only (sparse, `m = 2.5 n`); timings in a JVM depend on JIT, garbage collection and other load on the machine, so run-to-run spread is visible in the five raw values; the implementations use specific data structures (sorting a primitive `long[]` for Kruskal versus a boxed `PriorityQueue<Long>` for Prim), so the result compares these implementations, not the algorithms in the abstract. Dense graphs, other weight distributions and an indexed heap for Prim were not measured.

## 7. Sources 

**Sources.** The algorithms are the standard ones from the course material and from the textbook T. H. Cormen, C. E. Leiserson, R. L. Rivest, C. Stein, *Introduction to Algorithms* (disjoint sets, minimum spanning trees, depth-first search and topological sort, strongly connected components, single-source shortest paths in DAGs).
