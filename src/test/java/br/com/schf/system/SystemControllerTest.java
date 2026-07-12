package br.com.schf.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.schf.setup.SetupService;
import br.com.schf.setup.SetupStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import java.util.Properties;

@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

    @Mock
    private SetupService setupService;

    private SystemController controller;

    @BeforeEach
    void setUp() {
        var props = new InstanceProperties();
        props.setId("test-id");
        props.setName("Test Instance");
        props.setEnvironment("test");
        var build = new BuildProperties(new Properties());
        controller = new SystemController(props, setupService, build);
    }

    @Test
    void infoReturnsSanitizedResponseWhenSetupRequired() {
        when(setupService.getStatus()).thenReturn(new SetupStatusResponse(true));
        var response = controller.info();
        assertThat(response.productName()).isEqualTo("SCHF Core");
        assertThat(response.instanceId()).isEqualTo("test-id");
        assertThat(response.environment()).isEqualTo("test");
        assertThat(response.setupRequired()).isTrue();
    }

    @Test
    void infoReturnsSanitizedResponseWhenSetupComplete() {
        when(setupService.getStatus()).thenReturn(new SetupStatusResponse(false));
        var response = controller.info();
        assertThat(response.setupRequired()).isFalse();
    }

    @Test
    void capabilitiesIncludesExpectedFeatures() {
        when(setupService.getStatus()).thenReturn(new SetupStatusResponse(false));
        var response = controller.capabilities();
        assertThat(response.features()).contains("authentication", "rbac", "migration-import");
        assertThat(response.setupRequired()).isFalse();
    }
}
