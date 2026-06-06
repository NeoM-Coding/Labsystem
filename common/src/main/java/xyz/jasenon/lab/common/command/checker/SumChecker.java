package xyz.jasenon.lab.common.command.checker;

public class SumChecker {

    public static byte calculateCheckSum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        return (byte) (sum % 0xFF);
    }

    public static boolean verifyCheckSum(byte[] data){
        // 计算除最后一位外的所有字节的和
        int sum = 0;
        for (int i = 0; i < data.length - 1; i++) {
            sum += data[i];
        }

        byte checksum = (byte) (sum  % 0xFF);
        return checksum == data[data.length - 1];
    }

    public static byte calculateUnsignedByteCheckSum(byte[] data){
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i] & 0xff;  // 使用 & 0xFF 确保使用无符号字节值
        }
        return (byte) (sum % 0xFF);
    }

    public static boolean verifyUnsignedByteCheckSum(byte[] data){
        // 计算除最后一位外的所有字节的和
        int sum = 0;
        for (int i = 0; i < data.length - 1; i++) {
            sum += data[i] & 0xff;  // 使用 & 0xFF 确保使用无符号字节值
        }

        byte checksum = (byte) (sum % 0xFF);
        return checksum == data[data.length - 1];
    }

    public static byte[] generateSgPayload(byte[] originalPayload){
        byte[] payload = new byte[originalPayload.length+1];
        System.arraycopy(originalPayload,0,payload,0,originalPayload.length);
        payload[originalPayload.length] = calculateCheckSum(payload);
        return payload;
    }

    public static byte[] generateUnSgPayload(byte[] originalPayload){
        byte[] payload = new byte[originalPayload.length+1];
        System.arraycopy(originalPayload,0,payload,0,originalPayload.length);
        payload[originalPayload.length] = calculateUnsignedByteCheckSum(payload);
        return payload;
    }

}
