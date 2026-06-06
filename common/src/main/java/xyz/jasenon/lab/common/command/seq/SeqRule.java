package xyz.jasenon.lab.common.command.seq;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SeqRule {

    private final SeqType type;
    private final List<SeqFieldRule> fields;

    public SeqRule(SeqType type, List<SeqFieldRule> fields) {
        if (type == null) {
            throw new IllegalArgumentException("Seq type must not be null");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Seq fields must not be empty");
        }
        Set<String> names = new HashSet<>();
        for (SeqFieldRule field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("Seq field must not be null");
            }
            if (!names.add(field.getName())) {
                throw new IllegalArgumentException("Duplicate seq field: " + field.getName());
            }
        }
        this.type = type;
        this.fields = List.copyOf(fields);
    }

    public SeqType getType() {
        return type;
    }

    public List<SeqFieldRule> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SeqRule seqRule = (SeqRule) obj;
        return type == seqRule.type && Objects.equals(fields, seqRule.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fields);
    }
}
