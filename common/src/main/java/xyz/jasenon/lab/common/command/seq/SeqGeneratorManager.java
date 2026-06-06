package xyz.jasenon.lab.common.command.seq;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SeqGeneratorManager {

    private static final Map<SeqType, SeqGenerator> FACTORY = new ConcurrentHashMap<>();

    static {
        SeqRuleLoader.loadDefault();
    }

    private SeqGeneratorManager() {
    }

    public static void register(SeqType type, SeqGenerator generator) {
        if (type == null) {
            throw new IllegalArgumentException("Seq type must not be null");
        }
        if (generator == null) {
            throw new IllegalArgumentException("Seq generator must not be null");
        }
        FACTORY.put(type, generator);
    }

    public static SeqGenerator get(SeqType type) {
        if (type == null) {
            return null;
        }
        return FACTORY.get(type);
    }

    public static void clear() {
        FACTORY.clear();
    }
}
