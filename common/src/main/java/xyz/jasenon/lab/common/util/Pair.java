package xyz.jasenon.lab.common.util;

import lombok.Getter;

public class Pair<F,S> {
    public final F f;
    public final S s;

    private Pair(F f,S s){
        this.f = f;
        this.s = s;
    }

    public static <F,S> Pair<F,S> of(F f,S s){
        return new Pair<>(f,s);
    }
}
