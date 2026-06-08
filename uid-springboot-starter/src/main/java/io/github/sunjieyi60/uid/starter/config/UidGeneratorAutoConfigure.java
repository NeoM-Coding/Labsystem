package io.github.sunjieyi60.uid.starter.config;

import io.github.sunjieyi60.uid.starter.UidGenerator;
import io.github.sunjieyi60.uid.starter.component.CachedUidGenerator;
import io.github.sunjieyi60.uid.starter.component.DefaultUidGenerator;
import io.github.sunjieyi60.uid.starter.component.JpaIdentityGenerator;
import io.github.sunjieyi60.uid.starter.constant.Mode;
import io.github.sunjieyi60.uid.starter.properties.UidGeneratorProperties;
import io.github.sunjieyi60.uid.starter.worker.DefaultWorkerIdAssigner;
import io.github.sunjieyi60.uid.starter.worker.WorkerIdAssigner;
import io.github.sunjieyi60.uid.starter.buffer.RingBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * UidGenerator自动装配
 *
 * @author DengJun 2021/5/10
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(UidGeneratorProperties.class)
public class UidGeneratorAutoConfigure {
    /**
     * 基础生成器构建
     */
    @Bean
    @ConditionalOnMissingBean(UidGenerator.class)
    @ConditionalOnProperty(value = "fun.uid.generator-mode", havingValue = Mode.NONE, matchIfMissing = true)
    public UidGenerator defaultUidGenerator(UidGeneratorProperties properties, @Nullable WorkerIdAssigner assigner) {
        DefaultUidGenerator uidGenerator = new DefaultUidGenerator();
        if (assigner == null) {
            uidGenerator.setWorkerIdAssigner(new DefaultWorkerIdAssigner());
        } else {
            uidGenerator.setWorkerIdAssigner(assigner);
        }
        uidGenerator.setEpochStr(properties.getEpochStr());
        uidGenerator.setSeqBits(properties.getSeqBits());
        uidGenerator.setTimeBits(properties.getTimeBits());
        uidGenerator.setWorkerBits(properties.getWorkerBits());
        JpaIdentityGenerator.setInstance(uidGenerator);
        log.info("Default generator loading completed");
        return uidGenerator;
    }

    /**
     * 若环境中无Redis或MyBaits，且未配置 fun.uid.enable 任何值则注入该生成器
     * 基于缓冲内存可生成连续ID，具体存储结构见{@link RingBuffer}
     */
    @Bean
    @ConditionalOnMissingBean(UidGenerator.class)
    @ConditionalOnProperty(value = "fun.uid.generator-mode", havingValue = Mode.MEMORY)
    public UidGenerator cachedUidGenerator(UidGeneratorProperties properties, @Nullable WorkerIdAssigner assigner) {
        CachedUidGenerator uidGenerator = new CachedUidGenerator();
        if (assigner == null) {
            uidGenerator.setWorkerIdAssigner(new DefaultWorkerIdAssigner());
        } else {
            uidGenerator.setWorkerIdAssigner(assigner);
        }
        if (properties.getBoostPower() > 0) {
            uidGenerator.setBoostPower(properties.getBoostPower());
        }
        if (properties.getScheduleInterval() > 0) {
            uidGenerator.setScheduleInterval(properties.getScheduleInterval());
        }
        uidGenerator.setSeqBits(properties.getSeqBits());
        uidGenerator.setTimeBits(properties.getTimeBits());
        uidGenerator.setWorkerBits(properties.getWorkerBits());
        // slots是用于缓存已生成的id，当slots已满时，无法继续put时，触发拒绝策略
        uidGenerator.setRejectedPutBufferHandler((ringBuffer, uid) -> {
            log.info("RingBuffer has been full,cannot keep adding id");
        });
        // 当slots为空时，无法继续take时，触发拒绝策略
        uidGenerator.setRejectedTakeBufferHandler(ringBuffer -> {
            log.info("reject take buffer");
        });
        JpaIdentityGenerator.setInstance(uidGenerator);
        log.info("Memory generator loading completed");
        return uidGenerator;
    }

}
