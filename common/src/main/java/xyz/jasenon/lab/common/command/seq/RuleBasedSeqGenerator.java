package xyz.jasenon.lab.common.command.seq;

import java.util.StringJoiner;

public class RuleBasedSeqGenerator implements SeqGenerator {

    private final SeqRule rule;

    public RuleBasedSeqGenerator(SeqRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Seq rule must not be null");
        }
        this.rule = rule;
    }

    @Override
    public String generate(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }

        StringJoiner seq = new StringJoiner("|");
        for (SeqFieldRule field : rule.getFields()) {
            StringBuilder value = new StringBuilder();
            for (Integer index : field.getIndexes()) {
                if (index >= payload.length) {
                    throw new IllegalArgumentException("Payload length " + payload.length + " is less than seq index " + index);
                }
                value.append(String.format("%02X", payload[index] & 0xFF));
            }
            seq.add(field.getName() + "=" + value);
        }
        return seq.toString();
    }

    public SeqRule getRule() {
        return rule;
    }
}
