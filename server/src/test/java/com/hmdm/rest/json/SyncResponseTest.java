package com.hmdm.rest.json;

import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Settings;
import com.hmdm.util.CryptoUtil;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SyncResponseTest {

    @Test
    public void defaultDesignResponseAllowsMissingConfigurationPassword() {
        SyncResponse response = new SyncResponse(new Settings(), null, Collections.emptyList(), null);

        assertEquals(CryptoUtil.getMD5String(""), response.getPassword());
    }

    @Test
    public void configurationResponseAllowsMissingPassword() {
        SyncResponse response = new SyncResponse(new Configuration(), Collections.emptyList(), null);

        assertEquals(CryptoUtil.getMD5String(""), response.getPassword());
    }
}
