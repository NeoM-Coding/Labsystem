package xyz.jasenon.lab.common.util;

public class Triple <F,S,T>{

    public final F f;
    public final S s;
    public final T t;

    private Triple(F f,S s,T t){
        this.f = f;
        this.s = s;
        this.t = t;
    }

    public static <F,S,T> Triple<F,S,T> of (F f,S s,T t){
        return new Triple<>(f,s,t);
    }

}
