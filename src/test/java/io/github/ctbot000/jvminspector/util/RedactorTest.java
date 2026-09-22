package io.github.ctbot000.jvminspector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactorTest {

    @Test
    void secretNamesAreMasked() {
        Redactor redactor = new Redactor(Redactor.Mode.SECRETS);
        assertEquals(Redactor.MASK, redactor.named("AWS_SECRET_ACCESS_KEY", "abc"));
        assertEquals(Redactor.MASK, redactor.named("db.password", "hunter2"));
        assertEquals(Redactor.MASK, redactor.named("GITHUB_TOKEN", "abc"));
        assertEquals("/usr/bin", redactor.named("PATH", "/usr/bin"));
    }

    @Test
    void tokenShapesAreMaskedWhereverTheyAppear() {
        Redactor redactor = new Redactor(Redactor.Mode.SECRETS);
        assertEquals("key=" + Redactor.MASK, redactor.text("key=AKIAIOSFODNN7EXAMPLE"));
        assertTrue(redactor.text("ghp_0123456789abcdefghijklmnopqrstuvwx").contains(Redactor.MASK));
        assertFalse(redactor.text("a perfectly ordinary sentence").contains(Redactor.MASK));
    }

    @Test
    void noneLeavesEverythingAlone() {
        Redactor redactor = new Redactor(Redactor.Mode.NONE);
        assertEquals("hunter2", redactor.named("password", "hunter2"));
        assertEquals("10.1.2.3", redactor.host("10.1.2.3"));
    }

    @Test
    void allMasksIdentityAndAddresses() {
        Redactor redactor = new Redactor(Redactor.Mode.ALL);
        assertEquals(Redactor.MASK, redactor.host("de:ad:be:ef:00:01"));
        assertEquals(Redactor.MASK, redactor.host("192.168.0.7"));
        assertEquals("127.0.0.1", redactor.host("127.0.0.1"));
        String home = System.getProperty("user.home");
        assertEquals("~/work", redactor.host(home + "/work"));
    }

    @Test
    void anUnknownModeIsRejectedWithAUsefulMessage() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> Redactor.parseMode("sometimes"));
        assertTrue(failure.getMessage().contains("sometimes"));
    }
}
