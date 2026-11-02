package xyz.jasenon.lab.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class StorageTests {
    @TempDir Path dir;
    @Test void verifiedUploadRejectsDifferentReadback() throws Exception {
        Path file=dir.resolve("data");Files.writeString(file,"original");
        ObjectStorage storage=new ObjectStorage() {
            public void put(String b,String k,InputStream i,long size,String type) {}
            public InputStream get(String b,String k) { return new ByteArrayInputStream("corrupt".getBytes()); }
            public String downloadUrl(String b,String k,int s) { return ""; }
        };
        assertThatThrownBy(()->storage.uploadVerified("bucket","key",file,"application/gzip")).hasMessageContaining("checksum mismatch");
    }
    @Test void isolatesBucketsAndBoundsLinkLifetime() {
        assertThatThrownBy(()->new StorageProperties(null,null,null,null,null,"same","same")).isInstanceOf(IllegalArgumentException.class);
        var client=new MinioObjectStorage(new StorageProperties(null,null,"access","secretsecret",null,null,null));
        assertThatThrownBy(()->client.downloadUrl("lab-device-files","file",3601)).isInstanceOf(IllegalArgumentException.class);
    }
}
