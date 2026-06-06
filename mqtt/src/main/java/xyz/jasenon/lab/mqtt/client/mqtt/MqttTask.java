package xyz.jasenon.lab.mqtt.client.mqtt;

import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.command.checker.CheckType;
import xyz.jasenon.lab.common.command.checker.CrcChecker;
import xyz.jasenon.lab.common.command.checker.SumChecker;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;

import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;

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

    public static MqttTask fromDto(String gatewayId, Dto dto) {
        Objects.requireNonNull(dto, "dto must not be null");
        MqttTask task = new MqttTask(gatewayId);
        task.setCommand(dto.getCommandLine());
        task.setArgs(dto.getArgs());
        task.setType(dto.getType());
        task.setDeviceId(dto.getDeviceId());
        return task;
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

    public MqttTask convert(){
        return Explainer.explain(this);
    }

    private static String hex(int i){
        return String.format("%02x",i);
    }

    @Override
    public int hashCode(){
        return Objects.hash(
                getGatewayId(),
                type,
                deviceId
        );
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MqttTask task = (MqttTask) obj;
        return Objects.equals(getGatewayId(), task.getGatewayId())
                && type == task.type
                && Objects.equals(deviceId, task.deviceId);
    }

    static class Explainer {

        private static MqttTask explain(MqttTask task){
            if(task.commandLine == null) return null;
            Object[] args = Arrays.stream(task.args).mapToObj(MqttTask::hex).toArray(Object[]::new);
            String commandLine = MessageFormat.format(task.commandLine.getCommand().getCommandLine(), args);
            byte[] payload = Arrays.stream(commandLine.split(" "))
                    .map(hex -> (byte) Integer.parseInt(hex, 16))
                    .collect(ByteArrayOutputStream::new,
                            ByteArrayOutputStream::write,
                            (bos1,bos2)->{}).toByteArray();
            task.setPayload(checker(task.commandLine.getCommand().getCheckType(), payload));
            return task;
        }

        static byte[] checker(CheckType type, byte[] bytes){
            switch (type){
                case CRC16 -> {
                    return CrcChecker.generatePayload(bytes);
                }
                case SIGN_SUM -> {
                    return SumChecker.generateSgPayload(bytes);
                }
                case UNSIGN_SUM -> {
                    return SumChecker.generateUnSgPayload(bytes);
                }
            }
            return new byte[]{};
        }

        static boolean verifier(CheckType type, byte[] bytes){
            switch (type){
                case CRC16 -> {
                    return CrcChecker.varify(bytes);
                }
                case SIGN_SUM -> {
                    return SumChecker.verifyCheckSum(bytes);
                }
                case UNSIGN_SUM -> {
                    return SumChecker.verifyUnsignedByteCheckSum(bytes);
                }
            }
            return false;
        }

    }

    public PendingRequest<MqttTask> decorat(){
        return new PendingRequest<>(
                this, PendingRequest.Type.USER, 5000L
        );
    }
}
