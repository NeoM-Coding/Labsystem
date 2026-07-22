package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.rpc.AppResponse;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class BusinessExceptionDubboFilterTests {

    @Test
    void transportsBusinessCodeAndMessageAcrossRpcBoundary() {
        AppResponse providerResponse = new AppResponse(new BusinessException(403, "没有操作权限"));

        BusinessExceptionDubboFilter.encode(providerResponse);

        // Triple 会保留标准 RuntimeException 文本，但可能丢弃异常响应 attachment。
        AppResponse consumerResponse = new AppResponse(
                new RuntimeException(providerResponse.getException().getMessage()));
        BusinessExceptionDubboFilter.decode(consumerResponse);

        BusinessException exception = assertInstanceOf(
                BusinessException.class, consumerResponse.getException());
        assertEquals(403, exception.getCode());
        assertEquals("没有操作权限", exception.getMessage());
    }

    @Test
    void leavesNonBusinessExceptionUntouched() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        AppResponse response = new AppResponse(failure);

        BusinessExceptionDubboFilter.encode(response);
        BusinessExceptionDubboFilter.decode(response);

        assertSame(failure, response.getException());
    }

    @Test
    void mapsIllegalArgumentExceptionToBadRequest() {
        AppResponse providerResponse = new AppResponse(new IllegalArgumentException("字段格式错误"));

        BusinessExceptionDubboFilter.encode(providerResponse);
        AppResponse consumerResponse = new AppResponse(
                new RuntimeException(providerResponse.getException().getMessage()));
        BusinessExceptionDubboFilter.decode(consumerResponse);

        BusinessException exception = assertInstanceOf(
                BusinessException.class, consumerResponse.getException());
        assertEquals(400, exception.getCode());
        assertEquals("字段格式错误", exception.getMessage());
    }

    @Test
    void convertsInvalidRemoteCodeToControlledServerError() {
        AppResponse response = new AppResponse(new RuntimeException("remote failure"));
        response.setAttachment(BusinessExceptionDubboFilter.TYPE_ATTACHMENT, "true");
        response.setAttachment(BusinessExceptionDubboFilter.CODE_ATTACHMENT, "invalid");
        response.setAttachment(BusinessExceptionDubboFilter.MESSAGE_ATTACHMENT, "ignored");

        BusinessExceptionDubboFilter.decode(response);

        BusinessException exception = assertInstanceOf(
                BusinessException.class, response.getException());
        assertEquals(500, exception.getCode());
    }
}
