package xyz.jasenon.lab.base.compensation;
import java.util.Map;
public interface CompensationTaskHandler {
    String type();
    CompensationResult execute(Map<String, Object> payload);
}
