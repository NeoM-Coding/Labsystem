package xyz.jasenon.lab.auth.client;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

class AuthClientDataDeleteTests {

    @Test
    void scopesTupleAndAttributeDeletionToTheSameEntity() {
        var body = AuthClient.dataDeleteBody(SourceType.laboratory, "lab-1");

        assertThat(body.getTupleFilter()).isNotNull();
        assertThat(body.getTupleFilter().getEntity().getType()).isEqualTo("laboratory");
        assertThat(body.getTupleFilter().getEntity().getIds()).containsExactly("lab-1");
        assertThat(body.getAttributeFilter()).isNotNull();
        assertThat(body.getAttributeFilter().getEntity().getType()).isEqualTo("laboratory");
        assertThat(body.getAttributeFilter().getEntity().getIds()).containsExactly("lab-1");
    }
}
