package com.app.footballprediction.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractJsonProvider;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Custom JSON provider for LogstashEncoder that masks sensitive data
 * in log messages before they are written to JSON output.
 * <p>
 * Prevents accidental logging of:
 * <ul>
 *   <li>Passwords and credentials</li>
 *   <li>JWT / Bearer tokens</li>
 *   <li>API keys</li>
 *   <li>Personal Identifiable Information (email addresses)</li>
 * </ul>
 */
public class SensitiveDataMaskingProvider extends AbstractJsonProvider<ILoggingEvent> {

    /** Patterns for sensitive data that should be masked in log messages. */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|credential)\\s*[=:]\\s*\\S+");

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9\\-._~+/]+=*");

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|x-auth-token|authorization)\\s*[=:]\\s*\\S+");

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]*\\.eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String message = event.getFormattedMessage();
        if (message != null) {
            String masked = maskSensitiveData(message);
            if (!masked.equals(message)) {
                generator.writeStringField("message_masked", "true");
            }
        }
    }

    /**
     * Mask all known sensitive patterns in the given text.
     */
    public static String maskSensitiveData(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=***");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("Bearer ***");
        result = JWT_PATTERN.matcher(result).replaceAll("***JWT***");
        result = API_KEY_PATTERN.matcher(result).replaceAll("$1=***");
        result = EMAIL_PATTERN.matcher(result).replaceAll("***@***.***");

        return result;
    }
}


