package xyz.jasenon.lab.engine.definition.persistence.itfc;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.util.List;

public interface RuntimePersist {

    /**
     * 创建 Runtime 元数据及第一版不可变 revision。
     */
    boolean register(RuntimeRevision revision);

    /**
     * 追加新 revision，并将其切换为当前发布版本。
     */
    boolean update(String runtimeId, RuntimeRevision revision);

    /**
     * 软删除 Runtime，历史 revision 保留用于审计。
     */
    boolean remove(String runtimeId);

    boolean enable(String runtimeId);

    boolean disable(String runtimeId);

    /**
     * 查询每个未删除 Runtime 的当前发布 revision，包含禁用项。
     */
    List<RuntimeRevision> fetch();

}
