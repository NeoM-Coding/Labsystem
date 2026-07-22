package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.Address;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.SelfId;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class MqttTaskHelper implements TaskHelper {

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
        // 处理args参数
        List<Integer> stream = new ArrayList<>();
        if (device instanceof Address){
            stream.add(((Address) device).address());
        }
        if (device instanceof SelfId){
            stream.add(((SelfId) device).selfId());
        }
        for (int arg : dto.getArgs()){
            stream.add(arg);
        }
        int[] args = stream.stream().mapToInt(i -> (int) i).toArray();
        dto.setArgs(args);

        MqttTask task = MqttTask.fromDto(device.getGatewayId(), dto);
        task.setLaboratoryId(device.getBelongTo());
        return task;
    }


}
