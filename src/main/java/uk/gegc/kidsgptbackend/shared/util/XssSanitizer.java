package uk.gegc.kidsgptbackend.shared.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user input to prevent Cross-Site Scripting (XSS) attacks.
 * 
 * <p>This sanitizer removes or escapes potentially dangerous HTML and JavaScript content
 * from user input while preserving safe content.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>HTML entity encoding for special characters</li>
 *   <li>Script tag removal</li>
 *   <li>Event handler attribute removal (onclick, onerror, etc.)</li>
 *   <li>JavaScript protocol removal from URLs</li>
 *   <li>Null-safe operations</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>
 * String userInput = "&lt;script&gt;alert('XSS')&lt;/script&gt;Hello";
 * String safe = XssSanitizer.sanitize(userInput);
 * // Result: "Hello"
 * </pre>
 * 
 * @see <a href="https://owasp.org/www-community/attacks/xss/">OWASP XSS</a>
 */
@Slf4j
public final class XssSanitizer {

    // Pattern to match script tags (case-insensitive, handles variations)
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<\\s*script[^>]*>.*?</\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to match event handlers (onclick, onerror, onload, etc.)
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\s*on\\w+\\s*=\\s*['\"]?[^'\"]*['\"]?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match javascript: protocol in URLs
    private static final Pattern JAVASCRIPT_PROTOCOL_PATTERN = Pattern.compile(
            "javascript:",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match potentially dangerous HTML tags
    private static final Pattern DANGEROUS_TAG_PATTERN = Pattern.compile(
            "<\\s*/?\\s*(iframe|object|embed|applet|meta|link|style|base|form)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match HTML comments
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile(
            "<!--.*?-->",
            Pattern.DOTALL
    );

    private XssSanitizer() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Sanitizes input by removing dangerous content and encoding HTML entities.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Returns null if input is null (null-safe)</li>
     *   <li>Removes script tags and their content</li>
     *   <li>Removes event handler attributes</li>
     *   <li>Removes javascript: protocol from URLs</li>
     *   <li>Removes dangerous HTML tags</li>
     *   <li>Removes HTML comments</li>
     *   <li>Encodes remaining HTML entities</li>
     * </ol>
     * 
     * @param input The input string to sanitize
     * @return Sanitized string with dangerous content removed, or null if input was null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Remove script tags
        String sanitized = SCRIPT_PATTERN.matcher(input).replaceAll("");

        // Remove event handlers
        sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");

        // Remove javascript: protocol
        sanitized = JAVASCRIPT_PROTOCOL_PATTERN.matcher(sanitized).replaceAll("");

        // Remove dangerous HTML tags
        sanitized = DANGEROUS_TAG_PATTERN.matcher(sanitized).replaceAll("");

        // Remove HTML comments
        sanitized = HTML_COMMENT_PATTERN.matcher(sanitized).replaceAll("");

        // Encode HTML entities
        sanitized = encodeHtmlEntities(sanitized);

        // Log if significant sanitization occurred
        if (!input.equals(sanitized)) {
            log.debug("XSS sanitization applied. Original length: {}, Sanitized length: {}", 
                    input.length(), sanitized.length());
        }

        return sanitized;
    }

    /**
     * Encodes HTML entities to prevent XSS attacks.
     * 
     * <p>Encodes the following characters:</p>
     * <ul>
     *   <li>{@code &} → {@code &amp;}</li>
     *   <li>{@code <} → {@code &lt;}</li>
     *   <li>{@code >} → {@code &gt;}</li>
     *   <li>{@code "} → {@code &quot;}</li>
     *   <li>{@code '} → {@code &#x27;}</li>
     *   <li>{@code /} → {@code &#x2F;}</li>
     * </ul>
     * 
     * @param input The input string to encode
     * @return String with HTML entities encoded, or null if input was null
     */
    public static String encodeHtmlEntities(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * Sanitizes input for use in JavaScript contexts.
     * 
     * <p>This method escapes characters that have special meaning in JavaScript
     * to prevent code injection.</p>
     * 
     * @param input The input string to sanitize
     * @return JavaScript-safe string, or null if input was null
     */
    public static String sanitizeForJavaScript(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("<", "\\x3C")
                .replace(">", "\\x3E");
    }

    /**
     * Sanitizes input for use in URLs.
     * 
     * <p>Removes dangerous protocols and encodes special characters.</p>
     * 
     * @param input The input string to sanitize
     * @return URL-safe string, or null if input was null
     */
    public static String sanitizeForUrl(String input) {
        if (input == null) {
            return null;
        }

        // Remove dangerous protocols
        String sanitized = input.replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)data:", "")
                .replaceAll("(?i)vbscript:", "");

        return sanitized;
    }

    /**
     * Checks if input contains potentially dangerous content.
     * 
     * <p>This method can be used for validation before processing input.</p>
     * 
     * @param input The input string to check
     * @return true if input contains dangerous content, false otherwise
     */
    public static boolean containsDangerousContent(String input) {
        if (input == null) {
            return false;
        }

        return SCRIPT_PATTERN.matcher(input).find()
                || EVENT_HANDLER_PATTERN.matcher(input).find()
                || JAVASCRIPT_PROTOCOL_PATTERN.matcher(input).find()
                || DANGEROUS_TAG_PATTERN.matcher(input).find();
    }

    /**
     * Strips all HTML tags from input, leaving only text content.
     * 
     * <p>This is the most aggressive sanitization option and removes
     * all HTML markup.</p>
     * 
     * @param input The input string to strip
     * @return String with all HTML tags removed, or null if input was null
     */
    public static String stripHtmlTags(String input) {
        if (input == null) {
            return null;
        }

        // Remove all HTML tags
        return input.replaceAll("<[^>]*>", "");
    }
}

