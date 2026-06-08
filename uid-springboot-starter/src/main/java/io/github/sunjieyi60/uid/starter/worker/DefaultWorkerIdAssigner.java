package io.github.sunjieyi60.uid.starter.worker;

import org.apache.commons.lang3.RandomUtils;

/**
 * 默认任何使用内存的工作节点返回一个随机数，尽量避免节点相同
 *
 * @author DengJun 2021/5/11
 */
public class DefaultWorkerIdAssigner implements WorkerIdAssigner {
    @Override
    public long assignWorkerId() {
        return RandomUtils.nextInt(0, 1000);
    }
}