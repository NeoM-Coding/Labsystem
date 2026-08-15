package xyz.jasenon.lab.engine.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.ThreadParams;
import xyz.jasenon.lab.engine.eval.v2.EvalUpdate;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Eval v2 从事件索引入口到 Root 归并出口的端到端微基准。 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 8, time = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch"})
public class EvalForestBenchmark {

    @State(Scope.Benchmark)
    public static class SingleForestState {
        EvalV2ForestFixture.Scenario scenario;
        DeviceEventKey temperature;

        @Setup(Level.Trial)
        public void setup() {
            scenario = EvalV2ForestFixture.create(1);
            temperature = scenario.keysByDevice().get(0).get(0);
        }
    }

    @State(Scope.Benchmark)
    public static class SpreadForestState {
        @Param({"64", "1024"})
        int deviceCount;

        EvalV2ForestFixture.Scenario scenario;

        @Setup(Level.Trial)
        public void setup() {
            scenario = EvalV2ForestFixture.create(deviceCount);
        }
    }

    @State(Scope.Benchmark)
    public static class HighFanOutForestState {
        @Param({"64"})
        int deviceCount;

        @Param({"16", "64"})
        int treesPerDevice;

        @Param({"16", "64"})
        int leavesPerTree;

        EvalV2ForestFixture.HighFanOutScenario scenario;

        @Setup(Level.Trial)
        public void setup() {
            scenario = EvalV2ForestFixture.createHighFanOut(
                    deviceCount,
                    treesPerDevice,
                    leavesPerTree
            );
        }
    }

    @State(Scope.Thread)
    public static class Cursor {
        int threadIndex;
        int sequence;

        @Setup(Level.Trial)
        public void setup(ThreadParams threadParams) {
            threadIndex = threadParams.getThreadIndex();
        }

        int nextDevice(int deviceCount) {
            return Math.floorMod(threadIndex + sequence++ * 31, deviceCount);
        }

        String alternatingTemperature() {
            return (sequence++ & 1) == 0 ? "12" : "35";
        }
    }

    /** 单一热点字段持续翻转，测量 EventSourceNode 串行锁的吞吐上限。 */
    @Benchmark
    public EvalUpdate hotKeyFullPropagation(SingleForestState state, Cursor cursor) {
        return state.scenario.forest().accept(state.temperature, cursor.alternatingTemperature());
    }

    /** 同一字段重复发布相同值，测量最早无变化短路的理想上限。 */
    @Benchmark
    public EvalUpdate hotKeyImmediateShortCircuit(SingleForestState state) {
        return state.scenario.forest().accept(state.temperature, "28");
    }

    /** 将事件分散到多设备的五类字段，测量森林在多核下的扩展能力。 */
    @Benchmark
    public EvalUpdate spreadKeysMixedPropagation(SpreadForestState state, Cursor cursor) {
        int deviceIndex = cursor.nextDevice(state.deviceCount);
        List<DeviceEventKey> keys = state.scenario.keysByDevice().get(deviceIndex);
        int fieldIndex = Math.floorMod(cursor.sequence, keys.size());
        return state.scenario.forest().accept(
                keys.get(fieldIndex),
                value(fieldIndex, cursor.sequence)
        );
    }

    /**
     * 大树高扇出场景：一次事件会命中同一设备的全部树和全部叶子。
     * 该结果应连同 treesPerDevice、leavesPerTree 一起报告，不能只写裸 QPS。
     */
    @Benchmark
    public EvalUpdate highFanOutLargeTrees(HighFanOutForestState state, Cursor cursor) {
        int deviceIndex = cursor.nextDevice(state.deviceCount);
        String value = (cursor.sequence & 1) == 0 ? "0" : "10000";
        return state.scenario.forest().accept(
                state.scenario.keysByDevice().get(deviceIndex),
                value
        );
    }

    private static String value(int fieldIndex, int sequence) {
        boolean alternate = (sequence & 1) == 0;
        return switch (fieldIndex) {
            case 0 -> alternate ? "12" : "35";
            case 1 -> alternate ? "true" : "false";
            case 2 -> alternate ? "Cooling" : "Heating";
            case 3 -> alternate ? "0" : "1";
            case 4 -> alternate ? "Low" : "High";
            default -> throw new IllegalArgumentException("unknown field index: " + fieldIndex);
        };
    }
}
