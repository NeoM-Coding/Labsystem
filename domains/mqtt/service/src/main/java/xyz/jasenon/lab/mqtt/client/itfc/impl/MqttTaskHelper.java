package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.Address;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.SelfId;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.protocol.command.MqttCommandPolicy;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class MqttTaskHelper implements TaskHelper {

    private static final int BAD_REQUEST = 400;
    private final DeviceMapper deviceMapper;
    
    @Override
    public MqttTask help(MqttTaskDto dto) {
        Device device = deviceMapper.getDeviceById(dto.getDeviceId());
        if (device == null) return null;
        return help(device, dto);
    }

    @Override
    public MqttTask help(Device device, MqttTaskDto dto) {
        if (device == null || dto == null) return null;
        if (dto.getType() != device.getDeviceType()) {
            throw new BusinessException(BAD_REQUEST, "设备类型与实际设备不匹配");
        }
        int[] suppliedArgs = dto.getArgs() == null ? new int[0] : dto.getArgs();
        try {
            MqttCommandPolicy.validate(device.getDeviceType(), dto.getCommandLine(), suppliedArgs);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(BAD_REQUEST, error.getMessage());
        }
        // 处理args参数
        List<Integer> stream = new ArrayList<>();
        if (device instanceof Address){
            stream.add(((Address) device).address());
        }
        if (device instanceof SelfId){
            stream.add(((SelfId) device).selfId());
        }
        for (int arg : suppliedArgs){
            stream.add(arg);
        }
        int[] args = stream.stream().mapToInt(i -> (int) i).toArray();
        MqttTask task = MqttTask.fromDto(
                device.getGatewayId(),
                MqttTaskDto.of(dto.getCommandLine(), args, dto.getType(), dto.getDeviceId())
        );
        task.setLaboratoryId(device.getBelongTo());
        return task;
    }


}
