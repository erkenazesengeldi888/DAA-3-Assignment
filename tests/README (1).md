# Tests

All files are in `tests/`. Nothing here is discovered automatically.

| File | Content |
|---|---|
| `my_cases.jsonl` | 28 hand-made requests, one per line (all six tasks, mixed in one file). |
| `expected.jsonl` | Line *i* = expected answer for line *i* of `my_cases.jsonl`, with `name` and `why` (the hand justification). |
| `actual.jsonl` | Actual results of `Algorithms` for each line of `my_cases.jsonl`, written by `TestRunner`. |
| `TestRunner.java` | Test driver: calls the algorithms and `reconstructPath` directly and checks answers semantically. |
| `results.txt` | Full console output of one run of `TestRunner`. |

## Commands (from the repository root)

```sh
javac -d build/tests src/Java/Algorithms.java tests/TestRunner.java
java -cp build/tests TestRunner            # prints PASS/FAIL per check, exit code 1 on any failure
python tools/run.py --input tests/my_cases.jsonl   # the launcher: prints actual results through the real JSON adapter
python tools/run.py --check                         # 36 public cases
```

`TestRunner` also writes `tests/actual.jsonl`. Compare it with the launcher output to confirm the adapter returns the same data.

## How answers are checked

Non-unique answers are checked by properties, not by string equality.

- **Kruskal / Prim:** edge IDs are distinct, in range, not self-loops, acyclic (own DSU), exactly `n - c` of them, their weights sum to the reported cost, and the cost and component count equal the expected values.
- **SCC:** the partition must equal the expected partition exactly (labels are irrelevant), and the condensation must equal the expected set of cross-component pairs with no duplicates and no internal pairs.
- **topo:** every vertex exactly once, every edge goes forward; for cyclic graphs `order` is empty.
- **DAG:** `distance[source]=0`, each reachable vertex has a parent edge that *ends* at it and is tight (`dist[u]+w=dist[v]`), the parent chain reaches the source, and no edge can improve any distance. Together these prove the distances are exact.
- **Route helper:** every step must follow the stored edge in the right direction, and the edge weights must sum to `distance[target]`.

## Sections of `TestRunner`

1. **Hand-made cases** (28). Also repeats the batch in reverse order twice in one process and requires identical outputs (state reset).
2. **Witness tests.** Alternative valid answers are accepted (other forest ID order, other topological order, permuted SCC labels, both tied parent choices). Invalid ones are rejected (cycle in forest, suboptimal forest, repeated ID, self-loop ID, order violating an edge, merged/split SCCs, duplicate or missing condensation pair, parent edge not ending at the vertex, non-tight parent, wrong distance, reachable vertex reported as null).
3. **Route helper.** Reachable target, source, unreachable target, tied/parallel edges, source in the middle of the graph. Selected edge IDs, direction and total weight are printed.
4. **Random small graphs.** 3000 random graphs (n up to 6, loops, parallel edges, negative weights, many cycles). Oracles: brute force over all edge subsets for the minimum spanning forest, Floyd–Warshall for SCC and cycle detection, Bellman–Ford for DAG distances. Seed `20261022`.
5. **Full-size inputs.** DSU with 250000 operations and a 100000-vertex chain; Kruskal/Prim at `n=100000, m=250000` with random and extreme (`±10^9`) weights, a sparse graph with 70000 components, and a path with total `-99999·10^9`; chains of 100000 vertices for topo/SCC/DAG (forward, backward, closed into a cycle); a planted-SCC graph (1000 blocks × 100 vertices, expected components and condensation known by construction); a random DAG with 250000 edges; a cycle that is unreachable from the source at full size. The run uses `-Xss512k`-sized stacks safely because every DFS is iterative.

## Hand-made cases (`my_cases.jsonl` / `expected.jsonl`)

| # | Name | Why this test and the justified expected answer |
|---|---|---|
| 1 | `dsu_empty` | n=0 with no operations: no states are produced. |
| 2 | `dsu_example` | Statement example. 4 singletons; {0,1}; {0,1,2}; repeated union changes nothing; self-union changes nothing. |
| 3 | `dsu_merge_two_groups` | Start 5 comps. [0,1]->4,2; [2,3]->3,2; [1,0] repeat; [4,4] self; [1,3] merges two pairs->2,4; [3,4] joins last vertex->1,5. |
| 4 | `dsu_no_ops_single` | One vertex, no operations: states stay empty (no initial state is printed). |
| 5 | `kruskal_example` | Statement example. Cheapest edges 1 (w2) and 2 (w3) span {0,1,2}; edge 0 would close a cycle; vertex 3 isolated -> cost 5, 2 components. |
| 6 | `prim_example` | Same graph as kruskal_example solved by Prim; optimum cost and component count must agree. |
| 7 | `kruskal_loops_parallel_negative` | Loop with weight -5 must NOT be taken. Parallel 0-1: take -2 not 3. Two equal 1-2 edges of weight 7: take one. Components {0,1,2},{3},{4}: cost -2+7=5, 3 components, 2 edges. |
| 8 | `prim_loops_parallel_negative` | Same graph as above for Prim (restarts for isolated vertices 3 and 4). |
| 9 | `kruskal_total_beyond_32bit` | All weights 1e9; any spanning tree has 3 edges: 3,000,000,000 > 2^31-1. Tests 64-bit sums. |
| 10 | `prim_total_beyond_32bit` | Same graph for Prim. |
| 11 | `kruskal_empty` | n=0: cost 0, no edges, 0 components. |
| 12 | `prim_empty` | n=0 for Prim. |
| 13 | `scc_example` | Statement example. 0<->1 form one SCC; 2 and 3 are singletons. Cross pairs: {0,1}->{2} (twice, deduplicated), {2}->{3}. Reps (min vertex): [0,2],[2,3]. |
| 14 | `scc_duplicates_loop_isolate` | Cycle 0->1->2->0 is one SCC. Edges 2->3 (twice) and 1->3 give ONE pair {0,1,2}->{3}. Loop 3->3 is internal. 4->3 gives pair {4}->{3}. Vertex 5 isolated. |
| 15 | `scc_empty` | n=0: both arrays empty. |
| 16 | `scc_two_cycles` | SCCs {0,1} and {2,3,4}; only cross edge 1->2 -> single pair [0,2]. |
| 17 | `topo_example` | Statement example: 0 and 1 before 2, 2 before 3. Acyclic. |
| 18 | `topo_cycle` | Cycle 0->1->2->0 (vertex 3 hangs off it): not acyclic, order must be empty. |
| 19 | `topo_self_loop` | A self-loop is a cycle. |
| 20 | `topo_diamond_duplicates` | Diamond with a duplicate edge. Edge to an already FINISHED vertex (2->3 after 1->3) is not a cycle, so acyclic=true. |
| 21 | `topo_empty` | n=0: acyclic with empty order. |
| 22 | `dag_example` | Statement example. 0->1 (3), 1->2 (-7) gives dist[2]=-4 (better than 8), 2->3 (4) gives 0; vertex 4 unreachable -> null. |
| 23 | `dag_cycle_unreachable_from_source` | Cycle 2<->3 is NOT reachable from source 0, but the whole graph must be checked: acyclic=false and empty arrays. |
| 24 | `dag_parallel_and_tied` | Parallel edges 0->1 (ids 0,1, w5). Vertex 2 reachable by id3 (5) or by 0->1->2 (5+0): tie. Distances [0,5,5]; either parent is valid. |
| 25 | `dag_minus_one_is_finite` | Distance -1 for vertex 1 is a real value; vertex 2 is unreachable and must be null (not -1). |
| 26 | `dag_source_in_middle` | Source 2: vertices 0 and 1 (before the source in the chain) are unreachable; relaxation never starts from them. |
| 27 | `dag_single_vertex` | n=1: only the source, distance [0]. |
| 28 | `dag_self_loop` | A self-loop makes the graph cyclic. |

## Results

`results.txt` holds the complete output. Last run: **83 passed, 0 failed**. The timings printed there were measured in the environment where this run was made; run the commands above on your own machine and keep your own output.
