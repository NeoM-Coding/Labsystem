package xyz.jasenon.lab.base.context;

public class Holder {

    private static ThreadLocal<UserContext> Local = new ThreadLocal<>();

    public static void set(UserContext context){
        Local.set(context);
    }

    public static UserContext get(){
        return Local.get();
    }

    public static void clear(){
        Local.remove();
    }

}
