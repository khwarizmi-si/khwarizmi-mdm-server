package com.hmdm.rest.resource;

import com.hmdm.notification.persistence.domain.PushMessage;
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
}
