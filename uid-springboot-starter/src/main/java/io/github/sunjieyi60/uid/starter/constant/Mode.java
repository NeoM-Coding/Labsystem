package io.github.sunjieyi60.uid.starter.constant;

import io.github.sunjieyi60.uid.starter.component.CachedUidGenerator;
import io.github.sunjieyi60.uid.starter.worker.DefaultWorkerIdAssigner;

/**
 * @author DengJun 2021/5/11
 */
public interface Mode {
    String NONE = "none";
    String MEMORY = "memory";
    String REDIS = "redis";
    String DB = "db";

    /** 生成器模式 */
    enum Generator {
        /** 使用基础生成器（默认） */
        none,
        /** 使用内存生成器 {@link CachedUidGenerator}*/
        memory,
    }

    /** 节点分配器模式 */
    enum Assigner {
        /**
         * 默认使用随机生成 workerId
         * {@link DefaultWorkerIdAssigner}
         */
        none,
        /** 使用REDIS生成工作节点ID */
        redis,
        /** 使用MySQL生成工作节点ID */
        db;
    }
}
