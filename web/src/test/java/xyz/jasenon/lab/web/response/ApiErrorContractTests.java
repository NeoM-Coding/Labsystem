package xyz.jasenon.lab.web.response;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.serialize.Serialization;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.observability.rpc.RpcErrors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiErrorContractTests {
    @Test
    void deviceTimeoutSurvivesDubboSerializationAndRestAdvice() throws Exception {
        BusinessException original = new BusinessException(130001, 504,
                ErrorCode.MQTT_DEVICE_TIMEOUT.type(), ErrorCode.MQTT_DEVICE_TIMEOUT.message(), null, "trace-device-1");
        var invocation = new org.apache.dubbo.rpc.RpcInvocation();
        invocation.setMethodName("syncSend");
        invocation.setParameterTypes(new Class<?>[] { xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto.class });
        invocation.setArguments(new Object[] { new xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto() });
        var provider = new org.apache.dubbo.rpc.Invoker<xyz.jasenon.lab.api.mqtt.MqttIo>() {
            public Class<xyz.jasenon.lab.api.mqtt.MqttIo> getInterface() { return xyz.jasenon.lab.api.mqtt.MqttIo.class; }
            public URL getUrl() { return URL.valueOf("tri://localhost/error-contract?side=provider"); }
            public boolean isAvailable() { return true; }
            public void destroy() { }
            public org.apache.dubbo.rpc.Result invoke(org.apache.dubbo.rpc.Invocation request) {
                return org.apache.dubbo.rpc.AsyncRpcResult.newDefaultAsyncResult(
                        new CompletionException(new ExecutionException(original)), request);
            }
        };
        var chain = new org.apache.dubbo.rpc.cluster.filter.DefaultFilterChainBuilder()
                .buildInvokerChain(provider, "service.filter", "provider");
        RpcResult<?> failure = (RpcResult<?>) chain.invoke(invocation).getValue();
        Serialization codec = ExtensionLoader.getExtensionLoader(Serialization.class).getExtension("fastjson2");
        URL url = URL.valueOf("tri://localhost/error-contract");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var output = codec.serialize(url, bytes);
        output.writeObject(failure);
        output.flushBuffer();
        RpcResult<?> decoded = codec.deserialize(url, new ByteArrayInputStream(bytes.toByteArray())).readObject(RpcResult.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController(decoded))
                .setControllerAdvice(new BusinessExceptionHandler()).build();
        mvc.perform(get("/error-contract"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(130001))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.msg").value("设备响应超时，请检查设备连接"))
                .andExpect(jsonPath("$.traceId").value("trace-device-1"))
                .andExpect(jsonPath("$.httpStatus").doesNotExist());
    }

    @Test
    void httpStatusIsIndependentFromBusinessCode() {
        var entity = DiyResponseEntity.of(R.fail(409, 120123, "资源冲突"));
        assertThat(entity.getStatusCode().value()).isEqualTo(409);
        assertThat(entity.getBody().getCode()).isEqualTo(120123);
    }

    @Test
    void unknownFailuresHideImplementationDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new UnexpectedController())
                .setControllerAdvice(new BusinessExceptionHandler()).build();
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(100002))
                .andExpect(jsonPath("$.msg").value("服务器内部错误"));
    }

    @Test
    void requestBindingFailuresReturnTheSharedValidationCode() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new BusinessExceptionHandler()).build();
        mvc.perform(get("/validation").param("limit", "not-an-integer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001))
                .andExpect(jsonPath("$.msg").value("请求参数不合法"));
    }

    @RestController
    static class ValidationController {
        @GetMapping("/validation")
        public int get(@org.springframework.web.bind.annotation.RequestParam("limit") int limit) { return limit; }
    }

    @RestController
    static class FailureController {
        private final RpcResult<?> failure;
        FailureController(RpcResult<?> failure) { this.failure = failure; }
        @GetMapping("/error-contract")
        public Object getFailure() { return RpcClient.require(failure); }
    }
    @RestController
    static class UnexpectedController {
        @GetMapping("/unexpected")
        public Object getFailure() { throw new IllegalStateException("database secret"); }
    }
}
