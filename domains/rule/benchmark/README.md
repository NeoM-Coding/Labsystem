# Eval v2 JMH benchmark

这个模块只测量正式 `EvalForest.accept()` 的事件传播，不启动 Spring，也不把森林构建时间计入热路径。
森林拓扑与 `EvalV2DemoForest` 保持一致：每个设备包含 5 个事件源、12 个共享谓词和 4 棵表达式树。

## 构建

从 `lab-system-cloud` 根目录执行：

```bash
./mvnw -pl domains/rule/benchmark -am clean package -DskipTests
```

生成：

```text
domains/rule/benchmark/target/rule-engine-benchmarks.jar
```

## 运行

先查看全部基准：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar -l
```

单热点字段，分别使用 1、4、8、16 个发布线程：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar hotKeyFullPropagation -t 1 -prof gc
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar hotKeyFullPropagation -t 4 -prof gc
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar hotKeyFullPropagation -t 8 -prof gc
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar hotKeyFullPropagation -t 16 -prof gc
```

分散字段场景：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar spreadKeysMixedPropagation -t 8 -prof gc
```

大型表达式树、高扇出场景：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar highFanOutLargeTrees \
  -p deviceCount=64 \
  -p treesPerDevice=64 \
  -p leavesPerTree=64 \
  -t 8 \
  -prof gc
```

这个配置包含 64 个设备、4096 棵树、262144 个叶子。每次事件命中一个设备，
会重新计算该设备的 4096 个不同谓词，并传播到 64 棵、每棵 64 叶的组合网络。
报告结果时必须同时写明这三个规模参数；不能将小树的 `ops/s` 直接描述成复杂规则吞吐。

观察延迟分布时覆盖默认模式，并以微秒输出：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar hotKeyFullPropagation \
  -t 8 -bm sample -tu us -prof gc
```

保存 JSON 结果便于跨提交对比：

```bash
java -jar domains/rule/benchmark/target/rule-engine-benchmarks.jar \
  -t 8 -rf json -rff domains/rule/benchmark/target/jmh-result.json -prof gc
```

吞吐模式下 `Score` 的单位是 `ops/s`，一次 operation 就是一次 `EvalForest.accept()`。
它不是“叶子计算次数”：不同场景的一次 accept 所触发的谓词与组合节点数量可能相差数千倍。
若目标是 10 万事件每秒，应同时检查吞吐是否高于 `100000 ops/s`、误差范围、GC 的 `gc.alloc.rate.norm`，以及 SampleTime 模式下的 P99。
