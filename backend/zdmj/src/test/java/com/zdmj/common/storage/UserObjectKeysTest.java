package com.zdmj.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserObjectKeysTest {

    @Test
    void isOwnedBy_shouldAcceptExactUserSegment() {
        assertTrue(UserObjectKeys.isOwnedBy("user-1/resume/a.pdf", 1L));
        assertTrue(UserObjectKeys.isOwnedBy("/user-1/resume/a.pdf", 1L));
    }

    @Test
    void isOwnedBy_shouldRejectPrefixCollision() {
        assertFalse(UserObjectKeys.isOwnedBy("user-10/resume/a.pdf", 1L));
        assertFalse(UserObjectKeys.isOwnedBy("user-1/resume/a.pdf", 10L));
        assertFalse(UserObjectKeys.isOwnedBy("user-11/resume/a.pdf", 1L));
    }

    @Test
    void normalize_shouldRejectPathTraversal() {
        assertNull(UserObjectKeys.normalize("user-1/../user-2/secret.pdf"));
        assertNull(UserObjectKeys.normalize("user-1/resume/../x.pdf"));
        assertNull(UserObjectKeys.normalize("user-1//a.pdf"));
        assertNull(UserObjectKeys.normalize("user-1"));
        assertNull(UserObjectKeys.normalize(""));
        assertNull(UserObjectKeys.normalize(null));
    }

    @Test
    void ownedPrefix_shouldEndWithSlash() {
        assertEquals("user-1/", UserObjectKeys.ownedPrefix(1L));
        assertEquals("user-10/", UserObjectKeys.ownedPrefix(10L));
    }
}
