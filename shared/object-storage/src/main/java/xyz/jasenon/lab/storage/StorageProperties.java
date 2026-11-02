package xyz.jasenon.lab.storage;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("lab.storage")
public record StorageProperties(String endpoint, String publicEndpoint, String accessKey, String secretKey,
                                String region, String archiveBucket, String deviceBucket) {
    public StorageProperties {
        endpoint = endpoint == null ? "http://localhost:9000" : endpoint;
        publicEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        region = region == null ? "us-east-1" : region;
        archiveBucket = archiveBucket == null ? "lab-telemetry-archive" : archiveBucket;
        deviceBucket = deviceBucket == null ? "lab-device-files" : deviceBucket;
        if (archiveBucket.equals(deviceBucket)) throw new IllegalArgumentException("Storage buckets must be isolated");
    }
}
