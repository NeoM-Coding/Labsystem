package xyz.jasenon.lab.base.api.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationErrors {

    private final List<String> errors = new ArrayList<>();

    public void append(String error) {
        errors.add(error);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }
}
