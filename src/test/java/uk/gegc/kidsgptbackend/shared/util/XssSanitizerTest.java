package uk.gegc.kidsgptbackend.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XssSanitizer Tests")
class XssSanitizerTest extends BaseUnitTest {

    // ==================== sanitize() Tests ====================

    @Test
    @DisplayName("when input is null then returns null")
    void whenInputNull_thenReturnsNull() {
        // When
        String result = XssSanitizer.sanitize(null);
        
        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("when input is empty then returns empty")
    void whenInputEmpty_thenReturnsEmpty() {
        // When
        String result = XssSanitizer.sanitize("");
        
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("when input has no dangerous content then returns encoded entities")
    void whenInputSafe_thenReturnsEncodedEntities() {
        // Given
        String input = "Hello World";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("when input has script tag then removes it")
    void whenInputHasScriptTag_thenRemovesIt() {
        // Given
        String input = "<script>alert('XSS')</script>Hello";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("Hello");
    }

    @Test
    @DisplayName("when input has script tag with attributes then removes it")
    void whenInputHasScriptTagWithAttributes_thenRemovesIt() {
        // Given
        String input = "<script type='text/javascript' src='evil.js'>alert('XSS')</script>Test";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("Test");
    }

    @Test
    @DisplayName("when input has multiple script tags then removes all")
    void whenInputHasMultipleScriptTags_thenRemovesAll() {
        // Given
        String input = "<script>bad1()</script>Good<script>bad2()</script>Text";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("bad1");
        assertThat(result).doesNotContain("bad2");
        assertThat(result).contains("Good");
        assertThat(result).contains("Text");
    }

    @Test
    @DisplayName("when input has onclick handler then removes it")
    void whenInputHasOnClickHandler_thenRemovesIt() {
        // Given
        String input = "<div onclick='alert(1)'>Click me</div>";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("onclick");
        assertThat(result).doesNotContain("alert");
    }

    @Test
    @DisplayName("when input has onerror handler then removes it")
    void whenInputHasOnErrorHandler_thenRemovesIt() {
        // Given
        String input = "<img src='x' onerror='alert(1)'>";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("onerror");
        assertThat(result).doesNotContain("alert");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "onclick", "onload", "onerror", "onmouseover", "onmouseout",
        "onfocus", "onblur", "onchange", "onsubmit", "ondblclick"
    })
    @DisplayName("when input has event handlers then removes them")
    void whenInputHasEventHandlers_thenRemovesThem(String eventHandler) {
        // Given
        String input = String.format("<div %s='malicious()'>Test</div>", eventHandler);
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain(eventHandler);
        assertThat(result).doesNotContain("malicious");
    }

    @Test
    @DisplayName("when input has javascript protocol then removes it")
    void whenInputHasJavaScriptProtocol_thenRemovesIt() {
        // Given
        String input = "<a href='javascript:alert(1)'>Click</a>";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("when input has iframe tag then removes it")
    void whenInputHasIframeTag_thenRemovesIt() {
        // Given
        String input = "<iframe src='evil.com'></iframe>Safe text";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<iframe");
        assertThat(result).doesNotContain("evil.com");
        assertThat(result).contains("Safe text");
    }

    @Test
    @DisplayName("when input has object tag then removes it")
    void whenInputHasObjectTag_thenRemovesIt() {
        // Given
        String input = "<object data='evil.swf'></object>Text";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<object");
        assertThat(result).contains("Text");
    }

    @Test
    @DisplayName("when input has embed tag then removes it")
    void whenInputHasEmbedTag_thenRemovesIt() {
        // Given
        String input = "<embed src='evil.swf'>Text";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<embed");
        assertThat(result).contains("Text");
    }

    @Test
    @DisplayName("when input has HTML comment then removes it")
    void whenInputHasHtmlComment_thenRemovesIt() {
        // Given
        String input = "<!-- This is a comment -->Hello";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<!--");
        assertThat(result).doesNotContain("comment");
        assertThat(result).contains("Hello");
    }

    @Test
    @DisplayName("when input has special characters then encodes them")
    void whenInputHasSpecialCharacters_thenEncodesThem() {
        // Given
        String input = "<>&\"'/";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).contains("&lt;");
        assertThat(result).contains("&gt;");
        assertThat(result).contains("&amp;");
        assertThat(result).contains("&quot;");
        assertThat(result).contains("&#x27;");
        assertThat(result).contains("&#x2F;");
    }

    @Test
    @DisplayName("when input has complex XSS attack then sanitizes completely")
    void whenInputHasComplexXssAttack_thenSanitizesCompletely() {
        // Given
        String input = "<script>alert('XSS')</script><img src='x' onerror='alert(1)'><iframe src='javascript:alert(2)'></iframe>";
        
        // When
        String result = XssSanitizer.sanitize(input);
        
        // Then
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("onerror");
        assertThat(result).doesNotContain("<iframe");
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).doesNotContain("alert");
    }

    // ==================== encodeHtmlEntities() Tests ====================

    @Test
    @DisplayName("when encoding null then returns null")
    void whenEncodingNull_thenReturnsNull() {
        // When
        String result = XssSanitizer.encodeHtmlEntities(null);
        
        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("when encoding empty then returns empty")
    void whenEncodingEmpty_thenReturnsEmpty() {
        // When
        String result = XssSanitizer.encodeHtmlEntities("");
        
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("when encoding ampersand then returns entity")
    void whenEncodingAmpersand_thenReturnsEntity() {
        // Given
        String input = "Tom & Jerry";
        
        // When
        String result = XssSanitizer.encodeHtmlEntities(input);
        
        // Then
        assertThat(result).isEqualTo("Tom &amp; Jerry");
    }

    @Test
    @DisplayName("when encoding less than then returns entity")
    void whenEncodingLessThan_thenReturnsEntity() {
        // Given
        String input = "5 < 10";
        
        // When
        String result = XssSanitizer.encodeHtmlEntities(input);
        
        // Then
        assertThat(result).isEqualTo("5 &lt; 10");
    }

    @Test
    @DisplayName("when encoding greater than then returns entity")
    void whenEncodingGreaterThan_thenReturnsEntity() {
        // Given
        String input = "10 > 5";
        
        // When
        String result = XssSanitizer.encodeHtmlEntities(input);
        
        // Then
        assertThat(result).isEqualTo("10 &gt; 5");
    }

    @Test
    @DisplayName("when encoding quotes then returns entities")
    void whenEncodingQuotes_thenReturnsEntities() {
        // Given
        String input = "He said \"Hello\" and 'Hi'";
        
        // When
        String result = XssSanitizer.encodeHtmlEntities(input);
        
        // Then
        assertThat(result).contains("&quot;");
        assertThat(result).contains("&#x27;");
    }

    // ==================== sanitizeForJavaScript() Tests ====================

    @Test
    @DisplayName("when sanitizing for JavaScript with null then returns null")
    void whenSanitizingForJavaScriptWithNull_thenReturnsNull() {
        // When
        String result = XssSanitizer.sanitizeForJavaScript(null);
        
        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("when sanitizing for JavaScript with quotes then escapes them")
    void whenSanitizingForJavaScriptWithQuotes_thenEscapesThem() {
        // Given
        String input = "He said \"Hello\"";
        
        // When
        String result = XssSanitizer.sanitizeForJavaScript(input);
        
        // Then
        assertThat(result).contains("\\\"");
        assertThat(result).doesNotContain("\"Hello\"");
    }

    @Test
    @DisplayName("when sanitizing for JavaScript with newlines then escapes them")
    void whenSanitizingForJavaScriptWithNewlines_thenEscapesThem() {
        // Given
        String input = "Line1\nLine2";
        
        // When
        String result = XssSanitizer.sanitizeForJavaScript(input);
        
        // Then
        assertThat(result).contains("\\n");
        assertThat(result).doesNotContain("\n");
    }

    @Test
    @DisplayName("when sanitizing for JavaScript with angle brackets then escapes them")
    void whenSanitizingForJavaScriptWithAngleBrackets_thenEscapesThem() {
        // Given
        String input = "<script>";
        
        // When
        String result = XssSanitizer.sanitizeForJavaScript(input);
        
        // Then
        assertThat(result).contains("\\x3C");
        assertThat(result).contains("\\x3E");
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
    }

    // ==================== sanitizeForUrl() Tests ====================

    @Test
    @DisplayName("when sanitizing URL with null then returns null")
    void whenSanitizingUrlWithNull_thenReturnsNull() {
        // When
        String result = XssSanitizer.sanitizeForUrl(null);
        
        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("when sanitizing URL with javascript protocol then removes it")
    void whenSanitizingUrlWithJavaScriptProtocol_thenRemovesIt() {
        // Given
        String input = "javascript:alert(1)";
        
        // When
        String result = XssSanitizer.sanitizeForUrl(input);
        
        // Then
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).isEqualTo("alert(1)");
    }

    @Test
    @DisplayName("when sanitizing URL with data protocol then removes it")
    void whenSanitizingUrlWithDataProtocol_thenRemovesIt() {
        // Given
        String input = "data:text/html,<script>alert(1)</script>";
        
        // When
        String result = XssSanitizer.sanitizeForUrl(input);
        
        // Then
        assertThat(result).doesNotContain("data:");
    }

    @Test
    @DisplayName("when sanitizing URL with vbscript protocol then removes it")
    void whenSanitizingUrlWithVbScriptProtocol_thenRemovesIt() {
        // Given
        String input = "vbscript:msgbox(1)";
        
        // When
        String result = XssSanitizer.sanitizeForUrl(input);
        
        // Then
        assertThat(result).doesNotContain("vbscript:");
    }

    @Test
    @DisplayName("when sanitizing safe URL then preserves it")
    void whenSanitizingSafeUrl_thenPreservesIt() {
        // Given
        String input = "https://example.com/path?query=value";
        
        // When
        String result = XssSanitizer.sanitizeForUrl(input);
        
        // Then
        assertThat(result).isEqualTo(input);
    }

    // ==================== containsDangerousContent() Tests ====================

    @Test
    @DisplayName("when checking null then returns false")
    void whenCheckingNull_thenReturnsFalse() {
        // When
        boolean result = XssSanitizer.containsDangerousContent(null);
        
        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("when checking safe content then returns false")
    void whenCheckingSafeContent_thenReturnsFalse() {
        // Given
        String input = "Hello, World!";
        
        // When
        boolean result = XssSanitizer.containsDangerousContent(input);
        
        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("when checking script tag then returns true")
    void whenCheckingScriptTag_thenReturnsTrue() {
        // Given
        String input = "<script>alert('XSS')</script>";
        
        // When
        boolean result = XssSanitizer.containsDangerousContent(input);
        
        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("when checking event handler then returns true")
    void whenCheckingEventHandler_thenReturnsTrue() {
        // Given
        String input = "<div onclick='alert(1)'>Click</div>";
        
        // When
        boolean result = XssSanitizer.containsDangerousContent(input);
        
        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("when checking javascript protocol then returns true")
    void whenCheckingJavaScriptProtocol_thenReturnsTrue() {
        // Given
        String input = "<a href='javascript:alert(1)'>Link</a>";
        
        // When
        boolean result = XssSanitizer.containsDangerousContent(input);
        
        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("when checking iframe then returns true")
    void whenCheckingIframe_thenReturnsTrue() {
        // Given
        String input = "<iframe src='evil.com'></iframe>";
        
        // When
        boolean result = XssSanitizer.containsDangerousContent(input);
        
        // Then
        assertThat(result).isTrue();
    }

    // ==================== stripHtmlTags() Tests ====================

    @Test
    @DisplayName("when stripping HTML from null then returns null")
    void whenStrippingHtmlFromNull_thenReturnsNull() {
        // When
        String result = XssSanitizer.stripHtmlTags(null);
        
        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("when stripping HTML from plain text then returns same")
    void whenStrippingHtmlFromPlainText_thenReturnsSame() {
        // Given
        String input = "Hello, World!";
        
        // When
        String result = XssSanitizer.stripHtmlTags(input);
        
        // Then
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("when stripping HTML tags then removes all")
    void whenStrippingHtmlTags_thenRemovesAll() {
        // Given
        String input = "<p>Hello</p><b>World</b>";
        
        // When
        String result = XssSanitizer.stripHtmlTags(input);
        
        // Then
        assertThat(result).isEqualTo("HelloWorld");
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
    }

    @Test
    @DisplayName("when stripping complex HTML then removes all tags")
    void whenStrippingComplexHtml_thenRemovesAllTags() {
        // Given
        String input = "<div class='test'><p>Hello</p><span style='color:red'>World</span></div>";
        
        // When
        String result = XssSanitizer.stripHtmlTags(input);
        
        // Then
        assertThat(result).isEqualTo("HelloWorld");
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
    }

    @Test
    @DisplayName("when stripping HTML with attributes then removes everything")
    void whenStrippingHtmlWithAttributes_thenRemovesEverything() {
        // Given
        String input = "<a href='http://example.com' target='_blank'>Link</a>";
        
        // When
        String result = XssSanitizer.stripHtmlTags(input);
        
        // Then
        assertThat(result).isEqualTo("Link");
        assertThat(result).doesNotContain("href");
        assertThat(result).doesNotContain("target");
    }
}

