package xyz.jasenon.lab.common.util;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

public class DiyResponseEntity<T extends R<?>> extends ResponseEntity<T> {

    private DiyResponseEntity(T data){
        super(
                data,
                HttpStatusCode.valueOf(data.getCode())
        );
    }

    public static <T extends R<?>> DiyResponseEntity<T> of(T data){
        return new DiyResponseEntity<>(data);
    }
}
