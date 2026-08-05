package xyz.jasenon.lab.engine.runtime;

/**
 * Runtime 内存实例的生命周期状态。
 */
public enum RuntimeState {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
