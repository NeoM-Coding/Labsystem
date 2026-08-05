package xyz.jasenon.lab.common.util;

import java.io.Serial;
import java.io.Serializable;

public class Pair<F, S> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public final F f;
    public final S s;

    private Pair(F f, S s){
        this.f = f;
        this.s = s;
    }

    public static <F, S> Pair<F, S> of(F f, S s){
        return new Pair<>(f,s);
    }
}
