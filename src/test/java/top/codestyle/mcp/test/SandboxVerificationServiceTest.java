package top.codestyle.mcp.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import top.codestyle.mcp.service.SandboxVerificationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxVerificationServiceTest {

    private final SandboxVerificationService sandboxVerificationService = new SandboxVerificationService();

    @AfterEach
    void clearSandboxModeProperties() {
        System.clearProperty("codestyle.sandbox.mode");
        System.clearProperty("codestyle.sandbox.docker.fallback.local");
    }

    @Test
    void shouldRejectCommandOutOfWhitelist() {
        SandboxVerificationService.ExecutionResult result = sandboxVerificationService.executeCommand(
                "rm -rf .",
                "ut-sandbox-whitelist",
                30);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("whitelist");
    }

    @Test
    void shouldRejectCommandWithShellControlOperators() {
        SandboxVerificationService.ExecutionResult result = sandboxVerificationService.executeCommand(
                "mvn -v && echo hacked",
                "ut-sandbox-operator",
                30);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("shell control operators");
    }

    @Test
    void shouldPreventPathTraversalOnWriteFile() {
        assertThatThrownBy(() -> sandboxVerificationService.writeFile("../evil.txt", "x"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Illegal path traversal");
    }

    @Test
    void shouldExecuteWhitelistedJavaCommand() {
        SandboxVerificationService.ExecutionResult result = sandboxVerificationService.executeCommand(
                "java -version",
                "ut-sandbox-java-version",
                60);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void shouldFallbackToLocalWhenDockerUnavailableAndFallbackEnabled() {
        SandboxVerificationService service = new StubSandboxVerificationService(false);
        System.setProperty("codestyle.sandbox.mode", "docker");
        System.setProperty("codestyle.sandbox.docker.fallback.local", "true");

        SandboxVerificationService.ExecutionResult result = service.executeCommand(
                "java -version",
                "ut-sandbox-docker-fallback",
                60);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void shouldFailWhenDockerUnavailableAndFallbackDisabled() {
        SandboxVerificationService service = new StubSandboxVerificationService(false);
        System.setProperty("codestyle.sandbox.mode", "docker");
        System.setProperty("codestyle.sandbox.docker.fallback.local", "false");

        SandboxVerificationService.ExecutionResult result = service.executeCommand(
                "java -version",
                "ut-sandbox-docker-fail",
                60);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("Docker is unavailable");
    }

    @Test
    void shouldReadSpringSandboxPropertiesWhenSysAndEnvNotSet() {
        SandboxVerificationService service = new StubSandboxVerificationService(false);
        MockEnvironment mockEnvironment = new MockEnvironment()
                .withProperty("sandbox.mode", "docker")
                .withProperty("sandbox.docker.fallback-local", "false");
        ReflectionTestUtils.setField(service, "environment", mockEnvironment);

        SandboxVerificationService.ExecutionResult result = service.executeCommand(
                "java -version",
                "ut-sandbox-spring-props",
                60);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("Docker is unavailable");
    }

    private static final class StubSandboxVerificationService extends SandboxVerificationService {
        private final boolean dockerAvailable;

        private StubSandboxVerificationService(boolean dockerAvailable) {
            this.dockerAvailable = dockerAvailable;
        }

        @Override
        protected boolean isDockerAvailable() {
            return dockerAvailable;
        }
    }
}
