package xyz.jasenon.lab.mqtt.client.mqtt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.SetQueue;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.command.seq.SeqGeneratorManager;
import xyz.jasenon.lab.common.command.seq.SeqRuleLoader;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.common.Poll;

import java.util.ArrayDeque;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientMatchTests {

    @BeforeEach
    void loadRules() {
        SeqGeneratorManager.clear();
        SeqRuleLoader.loadDefault();
    }

    @AfterEach
    void reloadDefaultRules() {
        SeqGeneratorManager.clear();
        SeqRuleLoader.loadDefault();
    }

    @Test
    void matchesWhenGatewayAndGeneratedSeqAreSame() {
        MqttTask req = request("gateway-1", new byte[]{1, 10, 2, (byte) 255, 0, 0, 13});
        Task resp = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertTrue(MqttClient.matches(req, resp));
    }

    @Test
    void doesNotMatchDifferentGateway() {
        MqttTask req = request("gateway-1", new byte[]{1, 10, 2, (byte) 255, 0, 0, 13});
        Task resp = new Task("gateway-2", new byte[]{1, 10, 2, (byte) 255, 13});

        assertFalse(MqttClient.matches(req, resp));
    }

    @Test
    void doesNotMatchWhenCommandLineMissing() {
        MqttTask req = new MqttTask("gateway-1");
        req.setPayload(new byte[]{1, 10, 2, (byte) 255, 0, 0, 13});
        Task resp = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertFalse(MqttClient.matches(req, resp));
    }

    @Test
    void doesNotMatchWhenRulesAreMissing() {
        SeqGeneratorManager.clear();
        MqttTask req = request("gateway-1", new byte[]{1, 10, 2, (byte) 255, 0, 0, 13});
        Task resp = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertFalse(MqttClient.matches(req, resp));
    }

    @Test
    void doesNotMatchWhenPayloadIsTooShort() {
        MqttTask req = request("gateway-1", new byte[]{1, 10});
        Task resp = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertFalse(MqttClient.matches(req, resp));
    }

    @Test
    void doesNotMatchDifferentFunctionCode() {
        MqttTask req = request("gateway-1", new byte[]{1, 10, 2, (byte) 255, 0, 0, 13});
        Task resp = new Task("gateway-1", new byte[]{1, 3, 1, (byte) 255, 13});

        assertFalse(MqttClient.matches(req, resp));
    }

    @Test
    void rejectsDuplicatePollForSameMqttTaskWhileActive() {
        SetQueue<Poll<MqttTask>> queue = new SetQueue<>(new HashSet<>(), new ArrayDeque<>());
        Poll<MqttTask> accessPoll = new Poll<>(pollTask("gateway-1", "access-1"), 1000L);
        Poll<MqttTask> sameAccessPoll = new Poll<>(pollTask("gateway-1", "access-1"), 1000L);
        Poll<MqttTask> sameAccessPollWithOtherArgs = new Poll<>(pollTask("gateway-1", "access-1", 2), 1000L);
        Poll<MqttTask> otherAccessPoll = new Poll<>(pollTask("gateway-1", "access-2"), 1000L);

        assertTrue(queue.offer(accessPoll));
        assertFalse(queue.offer(sameAccessPoll));
        assertFalse(queue.offer(sameAccessPollWithOtherArgs));
        assertTrue(queue.offer(otherAccessPoll));
    }

    @Test
    void userRequestCreatedFromDtoConvertsPayloadAndMatchesResponse() {
        MqttTask task = MqttTask.fromDto("gateway-1", accessDto("access-1")).convert();
        PendingRequest<MqttTask> userRequest = new PendingRequest<>(task, PendingRequest.Type.USER, 5000L);
        Task response = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertSame(task, userRequest.getRequest());
        assertEquals(PendingRequest.Type.USER, userRequest.getType());
        assertArrayEquals(new byte[]{1, 10, 2, (byte) 255, 0, 0, 13}, task.getPayload());
        assertTrue(MqttClient.matches(userRequest.getRequest(), response));
    }

    @Test
    void pollRequestCreatedFromDtoConvertsPayloadAndMatchesResponse() {
        MqttTask task = MqttTask.fromDto("gateway-1", accessDto("access-1")).convert();
        Poll<MqttTask> poll = new Poll<>(task, 5000L, 1000L);
        PendingRequest<MqttTask> pollRequest = poll.poll();
        Task response = new Task("gateway-1", new byte[]{1, 10, 2, (byte) 255, 13});

        assertSame(task, pollRequest.getRequest());
        assertEquals(PendingRequest.Type.POLL, pollRequest.getType());
        assertEquals(5000L, pollRequest.getTimeout());
        assertArrayEquals(new byte[]{1, 10, 2, (byte) 255, 0, 0, 13}, task.getPayload());
        assertTrue(MqttClient.matches(pollRequest.getRequest(), response));
    }

    private static MqttTask request(String gatewayId, byte[] payload) {
        MqttTask req = new MqttTask(gatewayId);
        req.setCommand(CommandLine.OPEN_ACCESS_ONCE);
        req.setPayload(payload);
        return req;
    }

    private static MqttTask pollTask(String gatewayId, String deviceId) {
        return pollTask(gatewayId, deviceId, 1);
    }

    private static MqttTask pollTask(String gatewayId, String deviceId, int arg) {
        MqttTask req = new MqttTask(gatewayId);
        req.setCommand(CommandLine.REQUEST_ACCESS_DATA);
        req.setArgs(new int[]{arg});
        req.setType(DeviceType.Access);
        req.setDeviceId(deviceId);
        return req;
    }

    private static MqttTask.Dto accessDto(String deviceId) {
        MqttTask.Dto dto = new MqttTask.Dto();
        dto.setCommandLine(CommandLine.OPEN_ACCESS_ONCE);
        dto.setArgs(new int[]{1});
        dto.setType(DeviceType.Access);
        dto.setDeviceId(deviceId);
        return dto;
    }
}
