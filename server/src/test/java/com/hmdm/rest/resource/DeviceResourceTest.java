package com.hmdm.rest.resource;

import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.rest.json.DeviceLocation;
import org.junit.Assert;
import org.junit.Test;

public class DeviceResourceTest {

    @Test
    public void getPushMessageTypeForDeviceCommandAllowsKnownCommandsOnly() {
        Assert.assertEquals(PushMessage.TYPE_LOCK, DeviceResource.getPushMessageTypeForDeviceCommand("lock"));
        Assert.assertEquals(PushMessage.TYPE_REBOOT, DeviceResource.getPushMessageTypeForDeviceCommand("reboot"));
        Assert.assertEquals(PushMessage.TYPE_WIPE, DeviceResource.getPushMessageTypeForDeviceCommand("wipe"));
    }

    @Test
    public void getPushMessageTypeForDeviceCommandRejectsUnknownCommands() {
        Assert.assertNull(DeviceResource.getPushMessageTypeForDeviceCommand(null));
        Assert.assertNull(DeviceResource.getPushMessageTypeForDeviceCommand(""));
        Assert.assertNull(DeviceResource.getPushMessageTypeForDeviceCommand("runCommand"));
        Assert.assertNull(DeviceResource.getPushMessageTypeForDeviceCommand(" lock "));
    }

    @Test
    public void isDeviceLocationUsableAcceptsOnlyMapSafeCoordinates() {
        DeviceLocation location = new DeviceLocation();
        location.setLat(-6.2);
        location.setLon(106.8);

        Assert.assertTrue(DeviceResource.isDeviceLocationUsable(location));

        location.setLat(null);
        Assert.assertFalse(DeviceResource.isDeviceLocationUsable(location));

        location.setLat(91.0);
        Assert.assertFalse(DeviceResource.isDeviceLocationUsable(location));

        location.setLat(-6.2);
        location.setLon(-181.0);
        Assert.assertFalse(DeviceResource.isDeviceLocationUsable(location));

        Assert.assertFalse(DeviceResource.isDeviceLocationUsable(null));
    }
}
