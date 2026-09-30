# 并行图遍历组件

Pair-wise GSB 标注任务仓库（第 16 批 / 235）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的图有上百万节点，单线程遍历太慢，并行化后又出现重复访问和结果不确定。请从零实现一个并行图遍历组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持并行 BFS 与并行 DFS 两种遍历，并行度可配置；2) 访问去重：同一节点在并发下只能被访问一次，需用原子标记验证不重复访问；3) 结果确定性：同一图与同一并行度下多次运行输出的访问顺序必须一致（需说明如何保证）；4) 支持按层输出 BFS 结果（每层节点集合），层内节点有序；5) 支持图规模统计：节点数、边数、遍历耗时与各线程处理节点数分布；6) 支持遍历中取消，取消后已处理结果仍可用且线程要能退出；7) 支持带环图与自环，不得因环而重复遍历或死循环；8) 测试覆盖并行与单线程结果一致、去重、确定性、分层输出、取消与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

## 实现说明（并行图遍历组件）

包：`com.example.gsb.graph`（公共 API）、`com.example.gsb.graph.internal`（引擎）。

### API

- `Graph.builder(n).addEdge(u,v).build()`：不可变有向图，邻接表构建时按目标 id 升序并去重，自环/重复边安全。
- `ParallelGraphTraverser.bfs(graph, start, parallelism)` / `.dfs(...)`：同步执行，`parallelism >= 1` 可配置。
- `ParallelGraphTraverser.run(...)`：异步执行，返回 `TraversalHandle`，可 `cancel()`、`await()`、`result()`；取消后返回部分结果（`cancelled()==true`）。
- `TraversalResult`：`order()` 访问顺序；`levels()` 仅 BFS 可用，按层返回、层内升序；`stats()` 返回节点数、边数、已访问数、耗时(ns/ms)、各线程处理节点数分布。

### 去重与原子标记（要求 2、7）

- BFS 与 DFS 都用 `VarHandle.compareAndSet(int[], …, 0, 1)` 对顶点数组做原子认领：同一节点只有一个线程 CAS 成功，恰被访问/展开一次；测试用"顺序长度 == 去重集合大小"验证。
- 已标记的节点不会再次展开，因此回边、环、自环都不可能造成重复遍历，总工作量 O(V+E)，必然终止（无死循环）。

### 确定性如何保证（要求 3）

核心原则：**线程调度只能影响"谁先算完"，不能影响输出位置**——所有可能随调度变化的中间结果都在确定性屏障上被归并/重排，输出顺序是图结构的纯函数。

- 并行 BFS（层同步 bulk-synchronous）：一层全部展开完成（屏障）后才合并；本层新发现的节点统一按 id 升序排序，再作为下一层 frontier。任意调度下，"第 k 层的升序集合"相同，因此 `order` 与每层集合逐节点确定，且与"每层升序的单线程 BFS"完全一致。层小（不足以分给各线程）时由编排线程内联执行，避免链式图的百万次屏障开销。
- 并行 DFS（并行 LIFO 探索 + 确定性归约）：多 worker 共享 LIFO 栈抢占式探索（深度优先偏向、带来真实并行加速），探索只负责可达性与 CAS 去重，**不决定输出位置**；收敛后以已发现节点集为限，按邻接 id 升序做一次迭代先序 DFS 归约。该归约是图结构的纯函数，所以同图多次运行、不同并行度（含 1）的输出逐节点一致，且与单线程迭代 DFS 完全一致（见 `ReferenceTraversal` 与 `TraversalOrderTest`，对 20 余张随机/环/稠密/稀疏图各跑多次比对）。

### 取消语义（要求 6）

- 取消信号是启动前即绑定到句柄的 `CancellationToken`（取消早于引擎启动也不丢失）；BFS 在层屏障、任务边界检查令牌，DFS worker 在展开与空闲等待处检查。
- DFS 用 active 计数 + Condition 的经典空闲/终止协议收敛，取消时置终止标记并 `signalAll`，所有 worker 在 ≤50µs 内退出；BFS 关闭工作池。守护线程，`handle.result()` 一定返回。
- 取消后已处理结果仍可用：BFS 返回已完成的完整层；DFS 的已发现集合沿发现树对根闭包，仍可归约出有效的部分先序结果（无重复）。

### 统计（要求 5）

`TraversalStats` 含 `vertexCount/edgeCount/visitedCount/elapsedNanos/elapsedMillis` 以及线程名→处理节点数的分布；各线程计数之和恒等于 `visitedCount == order.size()`，测试对此断言。

### 测试

`TraversalOrderTest`（并行 vs 单线程一致、去重、确定性、环/自环、2 万节点大图）、`BfsLevelsTest`（分层、层内有序、层与扁平顺序一致）、`CancellationTest`（两种遍历取消后部分结果可用、worker 退出、幂等取消）、`TraversalStatsTest`（规模/耗时/线程分布）、`ValidationTest`（非法并行度与顶点）。
