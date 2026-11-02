package xyz.jasenon.lab.storage;
import io.minio.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="MINIO_TEST_ENDPOINT",matches=".+")
class MinioIntegrationTests {
    @TempDir Path dir;
    @Test void streamsVerifiesAndSignsUsingPublicEndpoint() throws Exception {
        String endpoint=System.getenv("MINIO_TEST_ENDPOINT");
        String bucket="bi-integration-"+java.util.UUID.randomUUID();
        var client=MinioClient.builder().endpoint(endpoint).credentials("testadmin","bi-test-only-password").build();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        var storage=new MinioObjectStorage(new StorageProperties(endpoint,endpoint,"testadmin","bi-test-only-password","us-east-1",null,null));
        Path file=dir.resolve("payload");Files.writeString(file,"verified telemetry");
        try {
            String sha=storage.uploadVerified(bucket,"device-files/test",file,"application/octet-stream");
            assertThat(sha).isEqualTo(ObjectStorage.digest(Files.newInputStream(file)));
            String url=storage.downloadUrl(bucket,"device-files/test",30);
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isEqualTo("verified telemetry");
            var denied=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint+"/"+bucket+"/device-files/test")).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertThat(denied.statusCode()).isEqualTo(403);
        } finally {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object("device-files/test").build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }
}
