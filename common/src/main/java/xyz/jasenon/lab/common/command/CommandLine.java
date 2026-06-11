package xyz.jasenon.lab.common.command;

import xyz.jasenon.lab.common.command.checker.CheckType;
import xyz.jasenon.lab.common.command.seq.SeqType;

public enum CommandLine {

    OPEN_AIR_CONDITION_RS485(new Command("{0} {1} 01 FF FF FF FF FF FF",CheckType.SIGN_SUM),SeqType.AirConditionReq,SeqType.AirConditionResp,"打开空调"),
    CLOSE_AIR_CONDITION_RS485(new Command("{0} {1} 00 FF FF FF FF FF FF",CheckType.SIGN_SUM),SeqType.AirConditionReq,SeqType.AirConditionResp,"关闭空调"),
    ENHANCE_CONTROL_AIR_CONDITION(new Command("{0} {1} {2} {3} {4} {5} FF FF FF",CheckType.SIGN_SUM),SeqType.AirConditionReq,SeqType.AirConditionResp,"增强控制空调"),
    REQUEST_AIR_CONDITION_DATA_RS485(new Command("{0} {1} FF FF FF FF FF FF FF",CheckType.SIGN_SUM),SeqType.AirConditionReq,SeqType.AirConditionResp,"请求空调数据"),

    OPEN_ACCESS_ONCE(new Command("{0} 0A 02 FF 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"单次开门"),
    CLOSE_ACCESS_ONCE(new Command("{0} 0A 02 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"单次关门"),
    REQUEST_ACCESS_DATA(new Command("{0} 03 01 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"请求门禁数据"),
//    OPEN_ACCESS_PERSIST_LOCK(new Command("{0} 0A 01 FF FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开锁定"),
//    OPEN_ACCESS_PERSIST_UNLOCK(new Command("{0} 0A 01 FF 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开解锁"),
//    OPEN_ACCESS_PERSIST_KEEP(new Command("{0} 0A 01 FF 11 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开保持原状"),
//    CLOSE_ACCESS_PERSIST_LOCK(new Command("{0} 0A 01 00 FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关锁定"),
//    CLOSE_ACCESS_PERSIST_UNLOCK(new Command("{0} 0A 01 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关解锁"),
//    CLOSE_ACCESS_PERSIST_KEEP(new Command("{0} 0A 01 00 11 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关保持原状"),
//    KEEP_ACCESS_STATUS_LOCK(new Command("{0} 0A 01 11 FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"保持门禁锁定状态"),
//    KEEP_ACCESS_STATUS_UNLOCK(new Command("{0} 0A 01 11 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"保持门禁解锁状态"),
    SET_ACCESS_DELAY(new Command("{0} 0A 03 {1} 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"延时设定门禁"),

    OPEN_CIRCUITBREAK(new Command("{0} 10 00 19 00 01 02 00 01",CheckType.CRC16),SeqType.CircuitBreakReq,SeqType.CircuitBreakResp,"断路器合闸"),
    CLOSE_CIRCUITBREAK(new Command("{0} 10 00 19 00 01 02 00 00",CheckType.CRC16),SeqType.CircuitBreakReq,SeqType.CircuitBreakResp,"断路器分闸"),
    REQUEST_CIRCUITBREAK_DATA(new Command("{0} 03 00 18 00 74",CheckType.CRC16),SeqType.CircuitBreakReq,SeqType.CircuitBreakResp,"请求断路器数据"),

    OPEN_LIGHT(new Command("{0} 0A {1} FF 11 00",CheckType.UNSIGN_SUM),SeqType.LightReq,SeqType.LightResp,"打开灯光"),
    CLOSE_LIGHT(new Command("{0} 0A {1} 00 11 00",CheckType.UNSIGN_SUM),SeqType.LightReq,SeqType.LightResp,"关闭灯光"),
    LOCK_LIGHT(new Command("{0} 0A {1} 11 FF 00",CheckType.UNSIGN_SUM),SeqType.LightReq,SeqType.SensorResp,"锁定面包板"),
    UNLOCK_LIGHT(new Command("{0} 0A {1} 11 00 00",CheckType.UNSIGN_SUM),SeqType.LightReq,SeqType.LightResp,"解锁面包板"),
    REQUEST_LIGHT_DATA(new Command("{0} 03 {1} 00 00 00",CheckType.UNSIGN_SUM),SeqType.LightReq,SeqType.LightResp, "请求灯光数据"),

    REQUEST_SENSOR_DATA(new Command("{0} 03 {1} 00 00 00",CheckType.UNSIGN_SUM),SeqType.SensorReq,SeqType.SensorResp,"请求传感器数据"),;

    // 指令
    private final Command command;
    // 同步方式计算
    private final SeqType reqSeq;
    private final SeqType respSeq;
    // 指令功能描述
    private final String description;

    CommandLine(Command command, SeqType reqSeq, SeqType respSeq, String description){
        this.command = command;
        this.reqSeq = reqSeq;
        this.respSeq = respSeq;
        this.description = description;
    }

    public Command getCommand() {
        return command;
    }

    public SeqType getReqSeq() {
        return reqSeq;
    }

    public SeqType getRespSeq() {
        return respSeq;
    }

    public String getDescription() {
        return description;
    }

}
