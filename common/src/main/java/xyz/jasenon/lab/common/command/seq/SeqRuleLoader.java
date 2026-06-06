package xyz.jasenon.lab.common.command.seq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于加载自定义规则配置  类似protof
 */
public class SeqRuleLoader {

    private static final String DEFAULT_RULE_PATH = "seq-rules.seq";

    private SeqRuleLoader() {
    }

    public static List<SeqRule> loadDefault() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SeqRuleLoader.class.getClassLoader();
        }
        InputStream inputStream = classLoader.getResourceAsStream(DEFAULT_RULE_PATH);
        if (inputStream == null) {
            throw new IllegalStateException("Default seq rule file not found: " + DEFAULT_RULE_PATH);
        }
        return load(inputStream);
    }

    public static List<SeqRule> load(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Seq rule input stream must not be null");
        }
        return load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    public static List<SeqRule> load(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Seq rule reader must not be null");
        }

        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            List<SeqRule> rules = parse(bufferedReader);
            for (SeqRule rule : rules) {
                SeqGeneratorManager.register(rule.getType(), new RuleBasedSeqGenerator(rule));
            }
            return rules;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seq rules", e);
        }
    }

    private static List<SeqRule> parse(BufferedReader reader) throws IOException {
        List<SeqRule> rules = new ArrayList<>();
        String line;
        int lineNumber = 0;
        SeqType currentType = null;
        List<SeqFieldRule> currentFields = null;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String normalized = stripComment(line).trim();
            if (normalized.isEmpty()) {
                continue;
            }

            if (currentType == null) {
                if (!normalized.startsWith("seq ") || !normalized.endsWith("{")) {
                    throw error(lineNumber, "Expected seq block start");
                }
                String typeName = normalized.substring(4, normalized.length() - 1).trim();
                currentType = parseType(typeName, lineNumber);
                currentFields = new ArrayList<>();
                continue;
            }

            if ("}".equals(normalized)) {
                rules.add(new SeqRule(currentType, currentFields));
                currentType = null;
                currentFields = null;
                continue;
            }

            currentFields.add(parseField(normalized, lineNumber));
        }

        if (currentType != null) {
            throw error(lineNumber, "Unclosed seq block: " + currentType);
        }
        return rules;
    }

    private static SeqFieldRule parseField(String line, int lineNumber) {
        int separator = line.indexOf('=');
        if (separator <= 0 || separator == line.length() - 1) {
            throw error(lineNumber, "Expected field assignment");
        }

        String name = line.substring(0, separator).trim();
        String indexPart = line.substring(separator + 1).trim();
        String[] indexTokens = indexPart.split(",");
        List<Integer> indexes = new ArrayList<>(indexTokens.length);
        for (String indexToken : indexTokens) {
            String value = indexToken.trim();
            if (value.isEmpty()) {
                throw error(lineNumber, "Empty seq field index");
            }
            try {
                int index = Integer.parseInt(value);
                if (index < 0) {
                    throw error(lineNumber, "Seq field index must be non-negative");
                }
                indexes.add(index);
            } catch (NumberFormatException e) {
                throw error(lineNumber, "Seq field index must be an integer: " + value);
            }
        }
        return new SeqFieldRule(name, indexes);
    }

    private static SeqType parseType(String typeName, int lineNumber) {
        if (typeName.isBlank()) {
            throw error(lineNumber, "Seq type must not be blank");
        }
        try {
            return SeqType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw error(lineNumber, "Unknown seq type: " + typeName);
        }
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        if (comment >= 0) {
            return line.substring(0, comment);
        }
        return line;
    }

    private static IllegalArgumentException error(int lineNumber, String message) {
        return new IllegalArgumentException("Invalid seq rule at line " + lineNumber + ": " + message);
    }
}
