package com.lcq.aiagent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Run explicitly with {@code -Dtest=EnvironmentImportCheck} after loading the target environment. */
class EnvironmentImportCheck {

    private static final StandardEnvironment ENVIRONMENT = new StandardEnvironment();
    private static final boolean PRINT_SUCCESS = Boolean.parseBoolean(
            System.getProperty("envCheck.printSuccess", "false"));

    @BeforeAll
    static void loadApplicationConfiguration() {
        ConfigDataEnvironmentPostProcessor.applyTo(ENVIRONMENT);
    }

    @Nested
    @DisplayName("OpenAI configuration")
    class OpenAiConfiguration {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "OPENAI_API_KEY, spring.ai.openai.api-key",
                "OPENAI_BASE_URL, spring.ai.openai.base-url",
                "OPENAI_MODEL, spring.ai.openai.chat.model",
                "OPENAI_REASONING_EFFORT, spring.ai.openai.chat.reasoning-effort",
                "OPENAI_MAX_COMPLETION_TOKENS, spring.ai.openai.chat.max-completion-tokens"
        })
        void importsOpenAiVariable(String variable, String property) {
            assertImported(variable, property);
        }
    }

    @Nested
    @DisplayName("HTTP server configuration")
    class HttpServerConfiguration {

        @Test
        @DisplayName("SERVER_PORT")
        void importsServerPort() {
            assertImported("SERVER_PORT", "server.port");
        }
    }

    private static void assertImported(String variable, String property) {
        String importedValue = System.getenv(variable);
        assertNotNull(importedValue, () -> variable + " is missing from the Java process. Check the .env import.");
        assertFalse(importedValue.isBlank(), () -> variable + " is blank in the Java process.");
        assertTrue(importedValue.equals(ENVIRONMENT.getProperty(property)),
                () -> variable + " does not match the resolved Spring property " + property + ".");
        printSuccess(variable, importedValue);
    }

    private static void printSuccess(String variable, String value) {
        if (!PRINT_SUCCESS) {
            return;
        }
        String displayValue = value;
        if ("OPENAI_API_KEY".equals(variable)) {
            displayValue = value.length() > 8 ? "****" + value.substring(value.length() - 4) : "****";
        }
        System.out.printf("[PASS] %s -> %s%n", variable, displayValue);
    }
}
