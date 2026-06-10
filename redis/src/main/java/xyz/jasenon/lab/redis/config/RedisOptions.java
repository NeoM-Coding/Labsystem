package xyz.jasenon.lab.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lab.redis")
public class RedisOptions {

    private boolean enabled = true;

    private String host = "localhost";

    private int port = 6379;

    private String username = "";

    private String password = "";

    private int database = 0;

    private int timeoutMillis = 2_000;

    private String keyPrefix = "lab";

    private Pool pool = new Pool();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = blankToDefault(host, "localhost");
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = positiveOrDefault(port, 6379);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = Math.max(database, 0);
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = positiveOrDefault(timeoutMillis, 2_000);
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = blankToDefault(keyPrefix, "lab");
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool == null ? new Pool() : pool;
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static class Pool {

        private int maxTotal = 16;

        private int maxIdle = 8;

        private int minIdle = 0;

        private long maxWaitMillis = 2_000L;

        private boolean testOnBorrow = true;

        public int getMaxTotal() {
            return maxTotal;
        }

        public void setMaxTotal(int maxTotal) {
            this.maxTotal = positiveOrDefault(maxTotal, 16);
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public void setMaxIdle(int maxIdle) {
            this.maxIdle = Math.max(maxIdle, 0);
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = Math.max(minIdle, 0);
        }

        public long getMaxWaitMillis() {
            return maxWaitMillis;
        }

        public void setMaxWaitMillis(long maxWaitMillis) {
            this.maxWaitMillis = maxWaitMillis <= 0 ? 2_000L : maxWaitMillis;
        }

        public boolean isTestOnBorrow() {
            return testOnBorrow;
        }

        public void setTestOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
        }
    }
}
