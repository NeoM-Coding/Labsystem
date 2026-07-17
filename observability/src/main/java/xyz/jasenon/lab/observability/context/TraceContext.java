package xyz.jasenon.lab.observability.context;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

public final class TraceContext {

    public static final String TRACE_ID = "trace_id";
    public static final String REQUEST_ID = "request_id";
    public static final String USER_ID = "user_id";
    public static final String USERNAME = "username";
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_HEADER = "X-Request-Id";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContext() {
    }

    public static Scope open(String traceId, String requestId) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.put(TRACE_ID, validOrGenerate(traceId, 16));
        MDC.put(REQUEST_ID, validOrGenerate(requestId, 12));
        return new Scope(previous);
    }

    public static String traceId() {
        return MDC.get(TRACE_ID);
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static void putUser(String userId, String username) {
        putIfPresent(USER_ID, userId);
        putIfPresent(USERNAME, username);
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value.trim());
        }
    }

    private static String validOrGenerate(String candidate, int bytes) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._:-]{1,128}")) {
            return candidate;
        }
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previous;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            MDC.clear();
            if (previous != null) {
                MDC.setContextMap(previous);
            }
        }
    }
}
