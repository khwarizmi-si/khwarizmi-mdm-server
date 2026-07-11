package com.hmdm.rest.resource;

import com.hmdm.notification.persistence.domain.PushMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DeviceResourceTest {

    @Test
    public void getDeviceCommandPushTypeAcceptsOnlySupportedRemoteCommands() {
        assertEquals(PushMessage.TYPE_REBOOT, DeviceResource.getDeviceCommandPushType("reboot"));
        assertEquals(PushMessage.TYPE_LOCK, DeviceResource.getDeviceCommandPushType("lock"));
        assertEquals(PushMessage.TYPE_WIPE, DeviceResource.getDeviceCommandPushType("wipe"));

        assertNull(DeviceResource.getDeviceCommandPushType(null));
        assertNull(DeviceResource.getDeviceCommandPushType(""));
        assertNull(DeviceResource.getDeviceCommandPushType("shutdown"));
    }
}
