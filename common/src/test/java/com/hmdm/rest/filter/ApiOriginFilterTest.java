package com.hmdm.rest.filter;

import org.junit.Assert;
import org.junit.Test;

public class ApiOriginFilterTest {

    @Test
    public void isAllowedOriginMatchesConfiguredOrigin() {
        ApiOriginFilter filter = new ApiOriginFilter("https://mdm.example.com, https://admin.example.com");

        Assert.assertTrue(filter.isAllowedOrigin("https://mdm.example.com"));
        Assert.assertTrue(filter.isAllowedOrigin("https://admin.example.com"));
    }

    @Test
    public void isAllowedOriginRejectsUnknownOrigin() {
        ApiOriginFilter filter = new ApiOriginFilter("https://mdm.example.com");

        Assert.assertFalse(filter.isAllowedOrigin("https://evil.example.com"));
    }

    @Test
    public void isAllowedOriginRejectsAllOriginsByDefault() {
        ApiOriginFilter filter = new ApiOriginFilter("");

        Assert.assertFalse(filter.isAllowedOrigin("https://mdm.example.com"));
    }
}
