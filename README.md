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

## 实现交付

从零实现了一个零外部依赖（仅 JUnit 5 / AssertJ 测试依赖）的并行图遍历组件，位于
`src/main/java/com/example/gsb/graph/`。

| 类 | 职责 |
|----|------|
| `Graph` | 不可变有向图，CSR（`offsets`+`edges`）紧凑表示；构建时邻接表排序、重边合并、保留自环 |
| `ParallelTraverser` | 核心组件：可配置并行度的并行 BFS / DFS，固定线程池、可复用，`AutoCloseable` |
| `CancellationToken` | 协作式取消句柄（`AtomicBoolean`），可在任意线程调用 |
| `TraversalResult` / `TraversalStats` | 不可变结果：访问顺序、BFS 分层、规模/耗时/各 worker 处理量、取消标记 |

### 用法

```java
CancellationToken token = new CancellationToken();
try (ParallelTraverser traverser = new ParallelTraverser(8)) {
    TraversalResult bfs = traverser.bfs(graph, 0, token);
    bfs.visitOrder();                 // 确定性访问顺序
    bfs.levels();                     // 按层输出，每层升序

    TraversalResult dfs = traverser.dfs(graph, 0, token);
    dfs.visitOrder();                 // p=1 时等价于顺序 DFS 前序
    dfs.stats();                      // 节点数/边数/耗时/各 worker 处理节点数
}
// token.cancel() 可在任意线程随时调用；已处理结果仍可读取，worker 线程会退出
```

### 去重如何保证（要求 2、7）

每个节点在 `AtomicIntegerArray` 中占一个标记位；节点被访问的唯一途径是一次成功的
`compareAndSet(0, 1)`，竞争失败的 worker 不再产生该节点。因此带环图、自环、多路径汇聚
都不会重复访问，也不会死循环（每节点至多入队一次，图有限则必然终止）。
`DeterminismTest.everyNodeVisitedExactlyOnceUnderHighContention` 用 700×700 稠密二层图
让 8 个线程高频竞争同一批汇聚节点，重复运行验证无重复。

### 确定性如何保证（要求 3）

访问顺序被设计成**图与并行度的纯函数，与线程调度无关**：

- **并行 BFS（层级同步）**：每层 frontier 按下标连续切块，worker i 永远处理第 i 块；
  所有 worker 完成本轮扩展后屏障同步，下一层按 worker 顺序合并再**按节点 id 排序**。
  BFS 深度是图本身的属性，与调度无关；层内排序进一步固定顺序。所以 BFS 在并行度
  1/2/3/8 下输出逐位相同，且等于“每层排序”的顺序参考实现。
- **并行 DFS（属主分区 + 轮次同步）**：节点按 `id % parallelism` 静态属主化，只有属主
  worker 能标记/入栈该节点；一轮内每个 worker 对自己的栈做普通 LIFO（逆序 push 有序
  邻接，最小 id 先出栈），跨属主的发现投入对应属主的桶；屏障后各桶排序、去重、逆序入栈。
  全局输出按“(轮次, worker id)”合并。调度只影响 worker **何时**运行，不影响其栈的
  **内容**，因此同一图、同一并行度下多次运行逐位相同；并行度为 1 时无跨属主边，输出
  与顺序 DFS 前序完全一致。

### 取消语义（要求 6）

worker 每处理一个节点都检查取消标记；取消后完成当前轮即返回，线程池线程及时退出（测试
用 `Future.get(10s)` 超时断言线程已退出，并复用同一 traverser 再跑一次完整遍历验证池仍
可用）。BFS 结果只含已完整发现的层；DFS 结果为取消前已处理节点的确定性前缀；统计中的
`visitedCount` 与返回顺序长度一致。取消可从其他线程发起
（`CancellationTest` 用 CountDownLatch 在访问到第 500 个节点时确定性触发）。

### 统计（要求 5）

`TraversalStats` 提供图节点数、去重后边数、已访问节点数、墙钟耗时（纳秒/毫秒）、
`nodesPerWorker` 各 worker 处理节点数分布（总和恒等于已访问数）。

### 规模验证

`LargeGraphTest`（默认跳过，`-Dlarge.tests=true` 开启）：100 万节点、400 万边随机图，
8 路并行 BFS 约 80ms、单线程约 120ms，各 worker 处理量均匀；并行 BFS 与单线程结果逐位
一致，DFS 两次运行逐位一致。

### 测试与需求映射

- 并行/单线程一致：`ParallelVsSequentialTest`（BFS 顺序逐位一致；DFS 并行度 1 等于顺序
  前序，其余并行度访问集合一致且无重复）
- 去重：`DeterminismTest`（高竞争稠密图）、`ParallelBfsTest`、`ParallelDfsTest`
- 确定性：`DeterminismTest`（BFS/DFS 各 10 次重复、多并行度对比）
- 分层输出：`ParallelBfsTest`（含已知图精确断言、层内有序性、随机图性质断言）
- 取消：`CancellationTest`（预取消、BFS/DFS 跨线程中途取消、线程退出、组件可复用）
- 统计：`StatsTest`（规模、耗时、分布求和、取消后统计一致）
- 环与自环：`ParallelBfsTest`、`ParallelDfsTest`
- 图结构：`GraphTest`（排序、去重、自环、边界校验）

```bash
mvn -q verify          # 33 个测试全部通过（大规模测试默认跳过）
mvn -q verify -Dlarge.tests=true
```
