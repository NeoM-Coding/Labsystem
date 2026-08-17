package xyz.jasenon.lab.engine.eval.v2;

/** 一条可显式解除的观察关系。 */
public interface Observation extends AutoCloseable {

    boolean active();

    @Override
    void close();
}
