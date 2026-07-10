package com.hmdm.rest.resource;

import org.junit.Assert;
import org.junit.Test;

public class ConfigurationFileResourceTest {

    @Test
    public void sanitizeUploadedFileNameAcceptsSimpleFileName() {
        Assert.assertEquals("config.json", ConfigurationFileResource.sanitizeUploadedFileName("config.json"));
    }

    @Test
    public void sanitizeUploadedFileNameRejectsPathTraversal() {
        assertInvalid("../config.json");
        assertInvalid("..\\config.json");
        assertInvalid("/tmp/config.json");
        assertInvalid("folder/config.json");
    }

    @Test
    public void sanitizeUploadedFileNameRejectsBlankAndSpecialNames() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid(".");
        assertInvalid("..");
        assertInvalid("bad\0name");
    }

    private static void assertInvalid(String fileName) {
        try {
            ConfigurationFileResource.sanitizeUploadedFileName(fileName);
            Assert.fail("Expected invalid file name: " + fileName);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
