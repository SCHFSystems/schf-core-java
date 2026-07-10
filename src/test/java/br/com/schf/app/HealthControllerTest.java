package br.com.schf.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void returnsJavaHealthMetadata() {
        var response = new HealthController().health();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.system()).isEqualTo("SCHF");
        assertThat(response.version()).isEqualTo("java-v2");
        assertThat(response.timestamp()).isNotNull();
    }
}
