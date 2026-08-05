package io.github.sunjieyi60.uid.starter.properties;

import io.github.sunjieyi60.uid.starter.constant.Mode;
import io.github.sunjieyi60.uid.starter.UidGenerator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UidGeneratorProperties -> https://github.com/baidu/uid-generator/blob/master/README.zh_cn.md
 * 以下为可选配置，若未配置将采用默认值
 *
 * @author MrDJun 2021/3/11
 * @refactor Jasenon 2026/5/1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fun.uid")
public class UidGeneratorProperties {
    /**
     * boostPower：RingBuffer size扩容参数, 可提高UID生成的吞吐量
     * 默认为3，原bufferSize=8192, 扩容后 bufferSize= 8192 << 3 = 65536
     */
    private int boostPower = 3;

    /**
     * 独立数据源配置（不与 Spring 主体共享 DataSource）
     */
    private Datasource datasource = new Datasource();

    @Getter
    @Setter
    public static class Datasource {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }

    /**
     * Specified bits & epoch as your demand. No specified the default value will be used\
     */
    private String epochStr;
    /**
     * 关于UID比特分配的建议：
     * 对于并发数要求不高、期望长期使用的应用, 可增加timeBits位数, 减少seqBits位数.
     * 例如节点采取用完即弃的WorkerIdAssigner策略, 重启频率为12次/天, 那么配置成 {"workerBits":23,"timeBits":31,"seqBits":9}时,
     * 可支持28个节点以整体并发量14400 UID/s的速度持续运行68年.
     * 对于节点重启频率频繁、期望长期使用的应用, 可增加workerBits和timeBits位数, 减少seqBits位数.
     * 例如节点采取用完即弃的WorkerIdAssigner策略, 重启频率为24*12次/天, 那么配置成{"workerBits":27,"timeBits":30,"seqBits":6}时,
     * 可支持37个节点以整体并发量 2400 UID/s 的速度持续运行34年.
     */
    private int seqBits;

    private int timeBits;

    private int workerBits;

    /**
     * 另外一种RingBuffer填充时机, 在Schedule线程中, 周期性检查填充
     * 默认:不配置此项, 即不使用Schedule线程. 如需使用请指定Schedule线程时间间隔, 单位:秒
     */
    private long scheduleInterval;

    /**
     * Generator模式 {@link Mode}
     * 见注释解释 {@link UidGenerator} 根据项目情况来选择是否开启缓存模式
     */
    private Mode.Generator generatorMode = Mode.Generator.none;
    /**
     * Assigner模式 {@link Mode}
     */
    private Mode.Assigner assignerMode = Mode.Assigner.none;
}
