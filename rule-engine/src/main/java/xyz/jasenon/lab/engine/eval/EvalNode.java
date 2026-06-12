package xyz.jasenon.lab.engine.eval;

import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.device.DeviceType;

@Getter
@Setter
public class EvalNode  {

    // 节点id
    private String nodeId;
    // 设备id
    private String deviceId;
    // 设备类型
    private DeviceType deviceType;
    // 字段名
    private String field;
    // 操作符
    private Operator operator;
    // 操作符右侧目标值
    private String value;
    // 当前节点与前置节点的计算关系
    private LogicType logicToPrev;
    // 节点当前的缓存值
    private volatile boolean result;

    // 提供iterator 支持 虚拟链表
    private EvalNode next;

}
