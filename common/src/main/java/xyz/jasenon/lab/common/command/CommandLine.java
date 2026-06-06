package xyz.jasenon.lab.common.command;

import xyz.jasenon.lab.common.command.checker.CheckType;
import xyz.jasenon.lab.common.command.seq.SeqType;

public enum CommandLine {

    OPEN_ACCESS_ONCE(new Command("{0} 0A 02 FF 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"单次开门"),
    CLOSE_ACCESS_ONCE(new Command("{0} 0A 02 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"单次关门"),
    REQUEST_ACCESS_DATA(new Command("{0} 03 01 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"请求门禁数据"),
    OPEN_ACCESS_PERSIST_LOCK(new Command("{0} 0A 01 FF FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开锁定"),
    OPEN_ACCESS_PERSIST_UNLOCK(new Command("{0} 0A 01 FF 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开解锁"),
    OPEN_ACCESS_PERSIST_KEEP(new Command("{0} 0A 01 FF 11 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长开保持原状"),
    CLOSE_ACCESS_PERSIST_LOCK(new Command("{0} 0A 01 00 FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关锁定"),
    CLOSE_ACCESS_PERSIST_UNLOCK(new Command("{0} 0A 01 00 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关解锁"),
    CLOSE_ACCESS_PERSIST_KEEP(new Command("{0} 0A 01 00 11 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"长关保持原状"),
    KEEP_ACCESS_STATUS_LOCK(new Command("{0} 0A 01 11 FF 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"保持门禁锁定状态"),
    KEEP_ACCESS_STATUS_UNLOCK(new Command("{0} 0A 01 11 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"保持门禁解锁状态"),
    SET_ACCESS_DELAY(new Command("{0} 0A 03 {1} 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"延时设定门禁"),;

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
