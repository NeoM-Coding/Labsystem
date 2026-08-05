package xyz.jasenon.lab.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PairTests {

    @Test
    void supportsSerializationAcrossRpcBoundary() throws Exception {
        Pair<Boolean, String> source = Pair.of(true, "轮询已开启");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(source);
        }

        Pair<?, ?> restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Pair<?, ?>) input.readObject();
        }

        assertThat(restored.f).isEqualTo(true);
        assertThat(restored.s).isEqualTo("轮询已开启");
    }
}
