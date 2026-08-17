package xyz.jasenon.lab.engine.eval.v2;

/** Runtime 对全局推理森林中一棵根的只读、可释放引用。 */
public interface EvalRootHandle extends AutoCloseable {

    EvalRootKey key();

    boolean value();

    boolean closed();

    @Override
    void close();
}
