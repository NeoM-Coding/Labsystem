# strategy

## 使用策略模式
```java
enum SeqType{
    AccessReq,
    AccessResp,
    ...ext
}
```

```java
class Handler {
    
    String generator(byte[] payload);
    
}
```
每一个enum 都有不同的计算seq的方式，
设备回复的payload和网关发送的payload也不同，
以CommandLine 为单位，发送时计算和接收的计算不同
这里给出一个CommandLine的示范
```java
enum CommandLine {
    OPEN_ACCESS_ONCE(new Command("{0} 0A 02 FF 00 00",CheckType.UNSIGN_SUM),SeqType.AccessReq,SeqType.AccessResp,"单次开门"),
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
}
```
OPEN_ACCESS_ONCE 对应的一条有效的hex req指令为 01 0A 02 FF 00 00 0D, resp为 01 0A 02 FF 0D,
我们处理为对应的byte[]
```json
{
  "req": [1,10,2,255,0,0,13],
  "resp": [1,10,2,255,13]
}
```
注意 req [1,10,2,255,0,0,13], index[0,1,2,3,4,5,6] 其中 index[0,1,2]分别对应address(index 0) function_code(index 1,2 是一个组合字段)
注意 resp [1,10,2,255,13], index[0,1,2,3,4,5,6] 其中 index[0,1,2]分别对应address(index 0) function_code(index 1,2 是一个组合字段)

handler 中generator函数接收的就是byte[] 要将req , resp匹配, 必须借助 address function_code self_id(这里没有体现出来)字段

主要是为了适配 mqtt模块中的 AbstractSysClient 中match 方法  提供消解方式 实际上他的两个入参必须保持的就是 最小 extends task
```java
class Task {
        // 网关id
        private String gatewayId;
        // 负载
        private byte[] payload;
        // 重写hashcode
        @Override
        public int hashCode(){
            return Objects.hash(gatewayId, Arrays.hashCode(payload));
        }
        // 重写equals
        @Override
        public boolean equals(Object obj){
            if(this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Task task = (Task) obj;
            return Objects.equals(gatewayId, task.gatewayId)
                    && Arrays.equals(payload, task.payload);
        }
}
```
不难发现 AbstractSysClient match方法消解req,resp就是通过处理 task中的payload进行,
其中volatile PendingRequest<MqttTask> current 保留了原始的commandLine
```java
public class MqttTask extends Task {
    // 指令
    private CommandLine commandLine;
    // 操作数
    private int[] args;
    // 设备类型
    private DeviceType type;
    // 设备id
    private String deviceId;
    public MqttTask(String gatewayId) {
        super(gatewayId, new byte[]{});
    }
    public CommandLine getCommand() {
        return commandLine;
    }
    public void setCommand(CommandLine commandLine) {
        this.commandLine = commandLine;
    }
    public int[] getArgs() {
        return args;
    }
    public void setArgs(int[] args) {
        this.args = args;
    }
    public DeviceType getType() {
        return type;
    }
    public void setType(DeviceType type) {
        this.type = type;
    }
    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    public static class Dto {
        // 指令
        private CommandLine commandLine;
        // 操作数
        private int[] args;
        // 设备类型
        private DeviceType type;
        // 设备id
        private String deviceId;
        public CommandLine getCommandLine() {
            return commandLine;
        }
        public void setCommandLine(CommandLine commandLine) {
            this.commandLine = commandLine;
        }
        public int[] getArgs() {
            return args;
        }
        public void setArgs(int[] args) {
            this.args = args;
        }
        public DeviceType getType() {
            return type;
        }
        public void setType(DeviceType type) {
            this.type = type;
        }
        public String getDeviceId() {
            return deviceId;
        }
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        public Dto() {
        }
    }
}
```
这样就为消解指明了SeqType。

这里的核心是，我想要通过类似protof的配置文件，让SeqRuleLoader载入这个配置文件，
指定对应SeqType取payload的哪几位作为 address function_code self_id，类似protof
参数的数字号一样

帮我设计这里的实现
