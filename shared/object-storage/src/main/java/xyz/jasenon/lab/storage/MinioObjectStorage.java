package xyz.jasenon.lab.storage;
import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient internal;
    private final MinioClient external;
    public MinioObjectStorage(StorageProperties p) {
        internal = client(p.endpoint(), p); external = client(p.publicEndpoint(), p);
    }
    private static MinioClient client(String endpoint, StorageProperties p) {
        return MinioClient.builder().endpoint(endpoint).credentials(p.accessKey(), p.secretKey()).region(p.region()).build();
    }
    @Override public void put(String bucket, String key, InputStream input, long size, String type) throws Exception {
        internal.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input, size, -1).contentType(type).build());
    }
    @Override public InputStream get(String bucket, String key) throws Exception {
        return internal.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    }
    @Override public String downloadUrl(String bucket, String key, int seconds) throws Exception {
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("Download lifetime must be 1..3600 seconds");
        return external.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                .bucket(bucket).object(key).expiry(seconds).build());
    }
}
