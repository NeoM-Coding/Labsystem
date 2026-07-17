package xyz.jasenon.lab.auth.context;

public final class UserContextHolder {

    public static final String DUBBO_ATTACHMENT_KEY = "user-context";
    public static final String LEGACY_DUBBO_ATTACHMENT_KEY = "user-conext";

    private static final ThreadLocal<UserContext> LOCAL = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        if (context == null) {
            clear();
            return;
        }
        LOCAL.set(context);
    }

    public static UserContext get() {
        return LOCAL.get();
    }

    public static void clear() {
        LOCAL.remove();
    }
}
