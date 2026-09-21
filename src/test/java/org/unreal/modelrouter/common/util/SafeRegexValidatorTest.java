package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafeRegexValidator 测试")
class SafeRegexValidatorTest {

    @Test
    void defaultPiiPatterns_shouldPassValidation() {
        assertNull(SafeRegexValidator.validateUserPattern("\\d{11}"));
        assertNull(SafeRegexValidator.validateUserPattern(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        assertNull(SafeRegexValidator.validateUserPattern(
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"));
        assertTrue(SafeRegexValidator.isSafeUserPattern("\\d{11}"));
    }

    @Test
    void invalidOrEmptyPattern_shouldBeRejected() {
        assertNotNull(SafeRegexValidator.validateUserPattern(null));
        assertNotNull(SafeRegexValidator.validateUserPattern("   "));
        assertNotNull(SafeRegexValidator.validateUserPattern("([unclosed"));
        assertFalse(SafeRegexValidator.isSafeUserPattern("([unclosed"));
        assertNull(SafeRegexValidator.compileOrNull("([unclosed"));
        assertThrows(IllegalArgumentException.class,
                () -> SafeRegexValidator.compileUserPattern("([unclosed"));
    }

    @Test
    void nestedQuantifier_shouldBeRejectedAsRedos() {
        assertFalse(SafeRegexValidator.isSafeUserPattern("(a+)+"));
        assertFalse(SafeRegexValidator.isSafeUserPattern("(a*)*"));
        assertFalse(SafeRegexValidator.isSafeUserPattern("([a-zA-Z]+)+"));
        assertNull(SafeRegexValidator.compileOrNull("(\\d+)+"));
    }

    @Test
    void quantifiedAlternation_andBackreference_shouldBeRejected() {
        assertFalse(SafeRegexValidator.isSafeUserPattern("(a|a)*"));
        assertFalse(SafeRegexValidator.isSafeUserPattern("(a|ab)+"));
        assertFalse(SafeRegexValidator.isSafeUserPattern("(\\w+)\\1"));
    }

    @Test
    void oversizedQuantifier_andOverlongPattern_shouldBeRejected() {
        assertFalse(SafeRegexValidator.isSafeUserPattern("a{20000}"));
        final String overlong = "a".repeat(SafeRegexValidator.MAX_PATTERN_LENGTH + 1);
        assertFalse(SafeRegexValidator.isSafeUserPattern(overlong));
    }

    @Test
    void compileUserPattern_shouldReturnCachedPattern() {
        final Pattern first = SafeRegexValidator.compileUserPattern("\\d{3}-\\d{4}");
        final Pattern second = SafeRegexValidator.compileUserPattern("\\d{3}-\\d{4}");
        assertSame(first, second);
        assertTrue(first.matcher("010-1234").find());
    }

    @Test
    void commonLookupPatterns_shouldStillCompile() {
        final Pattern email = SafeRegexValidator.compileOrNull(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        assertNotNull(email);
        assertTrue(email.matcher("user@example.com").find());

        final Pattern phone = SafeRegexValidator.compileOrNull("\\d{11}");
        assertNotNull(phone);
        assertTrue(phone.matcher("13800138000").find());
    }
}
