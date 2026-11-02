package xyz.jasenon.lab.web.response;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import xyz.jasenon.lab.common.util.R;

public class DiyResponseEntity<T extends R<?>> extends ResponseEntity<T> {

    private DiyResponseEntity(T data){
        super(
                data,
                HttpStatusCode.valueOf(data.getHttpStatus())
        );
    }

    public static <T extends R<?>> DiyResponseEntity<T> of(T data){
        if (data.getTraceId() == null) data.withTraceId(xyz.jasenon.lab.observability.context.TraceContext.traceId());
        return new DiyResponseEntity<>(data);
    }
}
