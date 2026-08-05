package xyz.jasenon.lab.engine.action;

public interface Action {
    default void invoke(){
        throw new UnsupportedOperationException("not implements yet!");
    }

    ActionType is();

    enum ActionType {
        Control,
        Report
    }
}
