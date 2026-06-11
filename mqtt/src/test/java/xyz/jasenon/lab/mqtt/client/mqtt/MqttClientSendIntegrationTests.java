package xyz.jasenon.lab.mqtt.client.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.command.seq.SeqGeneratorManager;
import xyz.jasenon.lab.common.command.seq.SeqRuleLoader;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.common.Poll;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MqttClientSendIntegrationTests {

    private static final String SERVER_URI = "tcp://localhost:1883";
    private static final String SEND_TOPIC = "test/accept/1";
    private static final String ACCEPT_TOPIC = "test/send/1";

    private static final String GATEWAY_ID = "gateway-1";
    private static final long RESPONSE_TIMEOUT_SECONDS = 10L;
    private static final long POLL_TIMEOUT_MILLIS = 3_000L;
    private static final long POLL_INTERVAL_MILLIS = 2_000L;
    private static final long POLL_OBSERVE_SECONDS = 15L;
    private static final int EXPECTED_POLL_COUNT = 5;
    private static final long MULTI_DEVICE_POLL_TIMEOUT_MILLIS = 1_000L;
    private static final long MULTI_DEVICE_POLL_INTERVAL_MILLIS = 2000L;
    private static final long MULTI_DEVICE_POLL_OBSERVE_SECONDS = 5L;
    private static final int MULTI_DEVICE_COUNT = 3;
    private static final long SERIAL_USER_TIMEOUT_MILLIS = 1_000L;
    private static final long SERIAL_POLL_TIMEOUT_MILLIS = 1_000L;
    private static final long SERIAL_POLL_INTERVAL_MILLIS = 2000L;
    private static final Logger log = LoggerFactory.getLogger(MqttClientSendIntegrationTests.class);

    private ProbeMqttClient client;

    @BeforeEach
    void connectClient() throws Exception {
        assumeFalse(SERVER_URI.isBlank(), "Fill SERVER_URI before running this integration test");
        assumeFalse(SEND_TOPIC.isBlank(), "Fill SEND_TOPIC before running this integration test");
        assumeFalse(ACCEPT_TOPIC.isBlank(), "Fill ACCEPT_TOPIC before running this integration test");

        SeqGeneratorManager.clear();
        SeqRuleLoader.loadDefault();

        client = new ProbeMqttClient(
                SERVER_URI,
                "mqtt-test-" + UUID.randomUUID(),
                GATEWAY_ID,
                SEND_TOPIC,
                ACCEPT_TOPIC
        );
        client.setCallback(new MqttCallback(client));
        client.connect(connectOptions());
    }

    @AfterEach
    void closeClient() throws Exception {
        try {
            if (client != null) {
                client.clearPollState();
                client.clearSerialState();
            }
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } finally {
            client = null;
            SeqGeneratorManager.clear();
            SeqRuleLoader.loadDefault();
        }
    }

    /**
     * Verifies the real user request path:
     * a USER {@link PendingRequest} is offered into {@code userQueue}, the worker
     * publishes it through the real MQTT client, and the matching response
     * completes the request future held by {@code AbstractSysClient.current}.
     */
    @Test
    void sendsRealMqttRequestAndMatchesAcceptedResponse() throws Exception {
        try {
            MqttTask request = MqttTask.fromDto(GATEWAY_ID, requestDto()).convert();
            client.offerUserAndWaitForMatchedResponse(request, RESPONSE_TIMEOUT_SECONDS);
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
        }
    }

    /**
     * Verifies the real poll path for one access device:
     * a {@link Poll} is offered into {@code pollQueue}, the worker converts it to
     * a POLL {@link PendingRequest}, sends it through MQTT, then finishes the
     * cycle by either receiving a matched response or timing out before the poll
     * is returned to the queue for the next interval.
     */
    @Test
    void pollQueueRepeatedlySendsRealMqttRequestsThroughWorker() throws Exception {
        MqttTask request = MqttTask.fromDto(GATEWAY_ID, pollDto()).convert();
        client.offerPollAndObserveSends(
                request,
                POLL_TIMEOUT_MILLIS,
                POLL_INTERVAL_MILLIS,
                POLL_OBSERVE_SECONDS,
                EXPECTED_POLL_COUNT
        );
    }

    /**
     * Verifies queue priority and serial execution:
     * several USER requests and one POLL request are offered to the same client,
     * and the single worker must execute all userQueue requests first, one at a
     * time, before it sends the queued poll request.
     */
    @Test
    void userQueueHasPriorityOverPollQueueAndRequestsRunSerially() throws Exception {
        MqttTask firstUserRequest = MqttTask.fromDto(GATEWAY_ID, requestDto()).convert();
        MqttTask secondUserRequest = MqttTask.fromDto(GATEWAY_ID, closeAccessDto()).convert();
        MqttTask pollRequest = MqttTask.fromDto(GATEWAY_ID, pollDto()).convert();
        PendingRequest<MqttTask> firstUserPending = new PendingRequest<>(
                firstUserRequest,
                PendingRequest.Type.USER,
                SERIAL_USER_TIMEOUT_MILLIS
        );
        PendingRequest<MqttTask> secondUserPending = new PendingRequest<>(
                secondUserRequest,
                PendingRequest.Type.USER,
                SERIAL_USER_TIMEOUT_MILLIS
        );

        Poll<MqttTask> poll = new Poll<>(pollRequest, SERIAL_POLL_TIMEOUT_MILLIS, SERIAL_POLL_INTERVAL_MILLIS);

        client.prepareSerialObservation(7);
        assertTrue(client.offerPoll(poll));
        assertTrue(client.offerUser(firstUserPending));
        assertTrue(client.offerUser(secondUserPending));

        assertTrue(
                client.awaitSerialExecution(5, TimeUnit.SECONDS),
                "Expected all user requests to finish before first poll request is sent"
        );
        assertEquals("send:user", client.events().get(0));
        assertUserFinished(client.events().get(1));
        assertEquals("send:user", client.events().get(2));
        assertUserFinished(client.events().get(3));
        assertEquals("send:poll", client.events().get(4));
        assertTrue(client.removePoll(poll));
    }

    /**
     * Verifies multi-device polling semantics:
     * multiple virtual Access devices can each register a distinct
     * {@code Poll<MqttTask>} because equality is based on gateway, type, and
     * device id, while a duplicate poll for the same device is rejected by
     * {@code SetQueue}. The worker then sends poll requests for each device
     * serially through the real MQTT client.
     */
    @Test
    void pollQueueAcceptsMultipleAccessDevicesAndSendsEachSerially() throws Exception {
        MqttTask firstAccess = MqttTask.fromDto(GATEWAY_ID, pollDto("access-1")).convert();
        MqttTask secondAccess = MqttTask.fromDto(GATEWAY_ID, pollDto("access-2")).convert();
        MqttTask thirdAccess = MqttTask.fromDto(GATEWAY_ID, pollDto("access-3")).convert();

        Poll<MqttTask> firstPoll = new Poll<>(firstAccess, MULTI_DEVICE_POLL_TIMEOUT_MILLIS, MULTI_DEVICE_POLL_INTERVAL_MILLIS);
        Poll<MqttTask> secondPoll = new Poll<>(secondAccess, MULTI_DEVICE_POLL_TIMEOUT_MILLIS, MULTI_DEVICE_POLL_INTERVAL_MILLIS);
        Poll<MqttTask> thirdPoll = new Poll<>(thirdAccess, MULTI_DEVICE_POLL_TIMEOUT_MILLIS, MULTI_DEVICE_POLL_INTERVAL_MILLIS);

        client.prepareMultiDevicePollObservation(MULTI_DEVICE_COUNT);

        try {
            assertTrue(client.offerPoll(firstPoll));
            assertTrue(client.offerPoll(secondPoll));
            assertTrue(client.offerPoll(thirdPoll));
            assertFalse(client.offerPoll(new Poll<>(firstAccess, MULTI_DEVICE_POLL_TIMEOUT_MILLIS, MULTI_DEVICE_POLL_INTERVAL_MILLIS)));

            assertTrue(
                    client.awaitMultiDevicePollSends(MULTI_DEVICE_POLL_OBSERVE_SECONDS, TimeUnit.SECONDS),
                    "Expected poll worker to send one poll request for each virtual access device"
            );
            assertEquals(Set.of("access-1", "access-2", "access-3"), client.polledDeviceIds());
            assertEquals(MULTI_DEVICE_COUNT, client.multiDevicePollEvents().size());
        } finally {
            client.removePoll(firstPoll);
            client.removePoll(secondPoll);
            client.removePoll(thirdPoll);
            client.clearMultiDevicePollState();
        }
    }

    private static MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(10);
        return options;
    }

    private static MqttTaskDto requestDto() {
        MqttTaskDto dto = new MqttTaskDto();
        dto.setCommandLine(CommandLine.OPEN_ACCESS_ONCE);
        dto.setArgs(new int[]{1});
        dto.setType(DeviceType.Access);
        dto.setDeviceId("access-1");
        return dto;
    }

    private static MqttTaskDto pollDto(){
        return pollDto("access-1");
    }

    private static MqttTaskDto pollDto(String deviceId){
        MqttTaskDto dto = new MqttTaskDto();
        dto.setCommandLine(CommandLine.REQUEST_ACCESS_DATA);
        dto.setArgs(new int[]{1});
        dto.setType(DeviceType.Access);
        dto.setDeviceId(deviceId);
        return dto;
    }

    private static MqttTaskDto closeAccessDto() {
        MqttTaskDto dto = new MqttTaskDto();
        dto.setCommandLine(CommandLine.CLOSE_ACCESS_ONCE);
        dto.setArgs(new int[]{1});
        dto.setType(DeviceType.Access);
        dto.setDeviceId("access-1");
        return dto;
    }

    private static void assertUserFinished(String event) {
        assertTrue(
                List.of("response:user", "timeout:user").contains(event),
                "Expected user request to finish before poll starts"
        );
    }

    private static class ProbeMqttClient extends MqttClient {

        private MqttTask currentRequest;
        private CountDownLatch pollSends;
        private CountDownLatch pollResponses;
        private CountDownLatch pollTimeouts;
        private AtomicInteger pollSendCount;
        private AtomicInteger pollResponseCount;
        private AtomicInteger pollTimeoutCount;
        private final List<String> serialEvents = new CopyOnWriteArrayList<>();
        private CountDownLatch serialExecution;
        private CountDownLatch multiDevicePollSends;
        private final List<String> multiDevicePollEvents = new CopyOnWriteArrayList<>();
        private final Set<String> polledDeviceIds = ConcurrentHashMap.newKeySet();

        private ProbeMqttClient(
                String serverURI,
                String clientId,
                String gatewayId,
                String sendTopic,
                String acceptTopic
        ) throws MqttException {
            super(serverURI, clientId, gatewayId, sendTopic, acceptTopic);
        }

        private void offerUserAndWaitForMatchedResponse(MqttTask request, long timeoutSeconds) throws Exception {
            this.currentRequest = request;

            PendingRequest<MqttTask> pendingRequest = new PendingRequest<>(
                    request,
                    PendingRequest.Type.USER,
                    TimeUnit.SECONDS.toMillis(timeoutSeconds)
            );
            assertTrue(offer(pendingRequest));

            Task response = (Task) pendingRequest.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("resp:{}",response.getPayload());
            assertTrue(MqttClient.matches(request, response));
        }

        private void offerPollAndObserveSends(
                MqttTask request,
                long timeoutMillis,
                long intervalMillis,
                long observeSeconds,
                int expectedPollCount
        ) throws Exception {
            this.currentRequest = request;
            this.pollSends = new CountDownLatch(expectedPollCount);
            this.pollResponses = new CountDownLatch(1);
            this.pollTimeouts = new CountDownLatch(1);
            this.pollSendCount = new AtomicInteger();
            this.pollResponseCount = new AtomicInteger();
            this.pollTimeoutCount = new AtomicInteger();

            Poll<MqttTask> poll = new Poll<>(request, timeoutMillis, intervalMillis);
            assertTrue(offer(poll));
            assertFalse(offer(new Poll<>(request, timeoutMillis, intervalMillis)));

            try {
                assertTrue(
                        pollSends.await(observeSeconds, TimeUnit.SECONDS),
                        "Expected poll worker to send at least " + expectedPollCount + " requests"
                );
                assertTrue(pollSendCount.get() >= expectedPollCount);
                assertTrue(
                        pollResponseCount.get() > 0 || pollTimeoutCount.get() > 0,
                        "Expected each poll cycle to finish through either matched response or timeout"
                );
            } finally {
                assertTrue(remove(poll));
                this.pollSends = null;
                this.pollResponses = null;
                this.pollTimeouts = null;
                this.pollSendCount = null;
                this.pollResponseCount = null;
                this.pollTimeoutCount = null;
            }
        }

        private void prepareSerialObservation(int expectedEvents) {
            serialEvents.clear();
            serialExecution = new CountDownLatch(expectedEvents);
        }

        private boolean awaitSerialExecution(long timeout, TimeUnit unit) throws InterruptedException {
            return serialExecution.await(timeout, unit);
        }

        private List<String> events() {
            return List.copyOf(serialEvents);
        }

        private boolean offerUser(PendingRequest<MqttTask> request) {
            this.currentRequest = request.getRequest();
            return offer(request);
        }

        private boolean offerPoll(Poll<MqttTask> poll) {
            return offer(poll);
        }

        private boolean removePoll(Poll<MqttTask> poll) {
            return remove(poll);
        }

        private void prepareMultiDevicePollObservation(int expectedDevices) {
            multiDevicePollEvents.clear();
            polledDeviceIds.clear();
            multiDevicePollSends = new CountDownLatch(expectedDevices);
        }

        private boolean awaitMultiDevicePollSends(long timeout, TimeUnit unit) throws InterruptedException {
            return multiDevicePollSends.await(timeout, unit);
        }

        private Set<String> polledDeviceIds() {
            return Set.copyOf(polledDeviceIds);
        }

        private List<String> multiDevicePollEvents() {
            return List.copyOf(multiDevicePollEvents);
        }

        private void clearMultiDevicePollState() {
            multiDevicePollSends = null;
            multiDevicePollEvents.clear();
            polledDeviceIds.clear();
        }

        private void clearPollState() {
            this.pollSends = null;
            this.pollResponses = null;
            this.pollTimeouts = null;
            this.pollSendCount = null;
            this.pollResponseCount = null;
            this.pollTimeoutCount = null;
        }

        private void clearSerialState() {
            this.serialEvents.clear();
            this.serialExecution = null;
        }

        @Override
        protected void send(MqttTask mqttTask) {
            log.info("current pending request type:{}", currentType());
            if (serialExecution != null) {
                if (mqttTask.getCommandLine() == CommandLine.REQUEST_ACCESS_DATA) {
                    recordSerialEvent("send:poll");
                } else {
                    recordSerialEvent("send:user");
                }
            }
            if (multiDevicePollSends != null && mqttTask.getCommandLine() == CommandLine.REQUEST_ACCESS_DATA) {
                polledDeviceIds.add(mqttTask.getDeviceId());
                multiDevicePollEvents.add(mqttTask.getDeviceId());
                multiDevicePollSends.countDown();
            }
            if (pollSends != null && currentRequest != null && currentRequest.equals(mqttTask)) {
                pollSendCount.incrementAndGet();
                pollSends.countDown();
            }
            super.send(mqttTask);
        }

        @Override
        protected void onMessage(Object resp) {
            log.info("resp:{}", ((Task) resp).getPayload());
        }

        @Override
        protected void onResponse(Object resp) {
            if (!(resp instanceof Task task)) {
                return;
            }
            if (serialExecution != null && task.getPayload()[1] == 10) {
                recordSerialEvent("response:user");
            }
            if (pollResponses != null && currentRequest != null && MqttClient.matches(currentRequest, task)) {
                    pollResponseCount.incrementAndGet();
                    pollResponses.countDown();
            }
        }

        @Override
        protected void onTimeout(MqttTask mqttTask, java.util.concurrent.TimeoutException e) {
            if (pollTimeouts != null && currentRequest != null && currentRequest.equals(mqttTask)) {
                pollTimeoutCount.incrementAndGet();
                pollTimeouts.countDown();
            }
            if (serialExecution != null && mqttTask.getCommandLine() != CommandLine.REQUEST_ACCESS_DATA) {
                recordSerialEvent("timeout:user");
            }
        }

        private void recordSerialEvent(String event) {
            serialEvents.add(event);
            serialExecution.countDown();
        }
    }
}
