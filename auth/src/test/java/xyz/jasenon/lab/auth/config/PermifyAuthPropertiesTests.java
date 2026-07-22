package xyz.jasenon.lab.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermifyAuthPropertiesTests {

    @Test
    void fallsBackToSafeDefaultForDepthBelowPermifyMinimum() {
        PermifyAuthProperties properties = new PermifyAuthProperties();

        properties.setDepth(2);

        assertThat(properties.getDepth()).isEqualTo(20);
    }

    @Test
    void acceptsPermifyMinimumDepth() {
        PermifyAuthProperties properties = new PermifyAuthProperties();

        properties.setDepth(3);

        assertThat(properties.getDepth()).isEqualTo(3);
    }
}
