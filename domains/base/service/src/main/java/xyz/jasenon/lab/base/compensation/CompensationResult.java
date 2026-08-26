package xyz.jasenon.lab.base.compensation;
public record CompensationResult(int affectedCount, String message) {
    public static CompensationResult success(int count, String message) {
        return new CompensationResult(count, message);
    }
}
