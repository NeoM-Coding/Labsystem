package xyz.jasenon.lab.common.command.seq;

import java.util.List;
import java.util.Objects;

public class SeqFieldRule {

    private final String name;
    private final List<Integer> indexes;

    public SeqFieldRule(String name, List<Integer> indexes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Seq field name must not be blank");
        }
        if (indexes == null || indexes.isEmpty()) {
            throw new IllegalArgumentException("Seq field indexes must not be empty");
        }
        for (Integer index : indexes) {
            if (index == null || index < 0) {
                throw new IllegalArgumentException("Seq field index must be a non-negative integer");
            }
        }
        this.name = name;
        this.indexes = List.copyOf(indexes);
    }

    public String getName() {
        return name;
    }

    public List<Integer> getIndexes() {
        return indexes;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SeqFieldRule that = (SeqFieldRule) obj;
        return Objects.equals(name, that.name) && Objects.equals(indexes, that.indexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, indexes);
    }
}
