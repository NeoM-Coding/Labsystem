package xyz.jasenon.lab.common.model;

import java.util.ArrayList;
import java.util.List;

public class Error {

    private final List<String> errors = new ArrayList<>();

    public void append(String error){
        errors.add(error);
    }

    public boolean error(){
        return errors.isEmpty();
    }

    public List<String> errors(){
        return this.errors;
    }

}
