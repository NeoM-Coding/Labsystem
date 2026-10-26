package xyz.jasenon.lab.engine.eval;

import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Predicate;

import java.util.*;

/**
 * Worker-confined left-fold tree. Only changed values bubble; event affectedness is indexed separately.
 */
public final class ConditionTree {
    private final Map<DeviceEventKey, List<Leaf>> devices = new HashMap<>();
    private final Map<DeviceEventKey, String> observations = new HashMap<>();
    private final Map<String, Node> windows = new HashMap<>();
    private final Map<String, PulseLeaf> points = new HashMap<>();
    private final Node root;

    public ConditionTree(List<Predicate> predicates, List<String> windowIds) {
        this(predicates, windowIds, List.of());
    }

    public ConditionTree(List<Predicate> predicates, List<String> windowIds, List<String> pointIds) {
        Node device = null;
        for (Predicate p : predicates) {
            Node leaf = new Node(false);
            devices.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(new Leaf(p, leaf));
            device = device == null ? leaf : new Node(p.logic(), device, leaf);
        }
        Node time = null;
        for (String id : windowIds) {
            Node leaf = new Node(false);
            windows.put(id, leaf);
            time = time == null ? leaf : new Node(LogicType.OR, time, leaf);
        }
        for (String id : pointIds) {
            PulseLeaf pulse = new PulseLeaf(new Node(false));
            points.put(id, pulse);
            time = time == null ? pulse.node() : new Node(LogicType.OR, time, pulse.node());
        }
        root = new Node(LogicType.AND, device == null ? new Node(true) : device, time == null ? new Node(true) : time);
    }

    public boolean value() {
        return root.value;
    }

    public Set<DeviceEventKey> eventKeys() {
        return Set.copyOf(devices.keySet());
    }

    public void observe(DeviceEventKey key, String value) {
        if (observations.containsKey(key) && Objects.equals(observations.get(key), value)) return;
        observations.put(key, value);
        for (Leaf leaf : devices.getOrDefault(key, List.of())) {
            Predicate p = leaf.predicate();
            boolean next;
            try {
                next = TypedValueParser.compare(key.deviceType(), key.field(), p.operator(), value, p.expected());
            } catch (IllegalArgumentException e) {
                next = false;
            }
            leaf.node().set(next);
        }
    }

    public boolean windows(Map<String, Boolean> statuses) {
        boolean changed = false;
        for (var entry : statuses.entrySet()) {
            Node leaf = windows.get(entry.getKey());
            if (leaf != null && leaf.value != entry.getValue()) {
                changed = true;
                leaf.set(entry.getValue());
            }
        }
        return changed;
    }

    public void pulses(Set<String> ids, boolean value) {
        ids.forEach(id -> {
            PulseLeaf leaf = points.get(id);
            if (leaf != null) leaf.node().set(value);
        });
    }

    private record PulseLeaf(Node node) {}

    private record Leaf(Predicate predicate, Node node) {
    }

    private static final class Node {
        boolean value;
        Node parent, left, right;
        LogicType logic;

        Node(boolean value) {
            this.value = value;
        }

        Node(LogicType logic, Node left, Node right) {
            this.logic = logic;
            this.left = left;
            this.right = right;
            left.parent = this;
            right.parent = this;
            value = calculate();
        }

        boolean calculate() {
            return logic == LogicType.AND ? left.value && right.value : left.value || right.value;
        }

        void set(boolean next) {
            if (value == next) return;
            value = next;
            for (Node n = parent; n != null; n = n.parent) {
                boolean v = n.calculate();
                if (n.value == v) break;
                n.value = v;
            }
        }
    }
}
