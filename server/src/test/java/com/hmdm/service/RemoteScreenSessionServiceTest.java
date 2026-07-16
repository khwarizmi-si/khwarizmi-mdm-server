package com.hmdm.service;

import com.hmdm.notification.PushSender;
import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.User;
import com.hmdm.rest.json.RemoteScreenControl;
import com.hmdm.rest.json.RemoteScreenFrame;
import com.hmdm.rest.json.RemoteScreenSession;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteScreenSessionServiceTest {
    private RecordingNotificationMapper notificationMapper;
    private RecordingPushSender mqttSender;
    private RemoteScreenSessionService service;

    @Before
    public void setUp() {
        User user = new User();
        user.setId(7);
        SecurityContext.init(user);

        notificationMapper = new RecordingNotificationMapper();
        mqttSender = new RecordingPushSender();
        PushService pushService = new PushService(mqttSender, new NoopPushSender(),
                null, null, notificationMapper);
        service = new RemoteScreenSessionService(pushService);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    @Test
    public void startClosesExistingDeviceSessionAndClearsPendingRemoteScreenPushes() {
        Device device = device(5, "tes01");

        RemoteScreenSession first = service.start(device);
        RemoteScreenSession second = service.start(device);

        assertEquals("ended", service.get(first.getId()).getStatus());
        assertEquals("pending", service.get(second.getId()).getStatus());
        assertEquals(5, notificationMapper.deviceId);
        assertEquals(2, notificationMapper.deleteCallCount);
        assertEquals(3, notificationMapper.messageTypes.size());
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_START, notificationMapper.messageTypes.get(0));
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_STOP, notificationMapper.messageTypes.get(1));
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_CONTROL, notificationMapper.messageTypes.get(2));
        assertEquals(3, mqttSender.messages.size());
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_START, mqttSender.messages.get(0).getMessageType());
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_STOP, mqttSender.messages.get(1).getMessageType());
        assertTrue(mqttSender.messages.get(1).getPayload().contains(first.getId()));
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_START, mqttSender.messages.get(2).getMessageType());
    }

    @Test
    public void stoppedSessionRejectsFurtherFrames() {
        RemoteScreenSession session = service.start(device(5, "tes01"));
        service.stop(session.getId());

        assertNull(service.updateFrame(session.getId(), frame()));
        assertEquals("ended", service.get(session.getId()).getStatus());
    }

    @Test
    public void expiredSessionRejectsFramesAndControls() {
        RemoteScreenSession session = service.start(device(5, "tes01"));
        session.setExpiresAt(0);

        assertEquals("expired", service.get(session.getId()).getStatus());
        assertNull(service.updateFrame(session.getId(), frame()));
        assertNull(service.control(session.getId(), control("home")));
    }

    @Test
    public void staleActiveSessionFailsInsteadOfShowingAnOldFrame() {
        RemoteScreenSession session = service.start(device(5, "tes01"));
        service.updateFrame(session.getId(), frame());
        session.setFrameUpdatedAt(1);

        RemoteScreenSession result = service.get(session.getId());

        assertEquals("failed", result.getStatus());
        assertEquals("frame_timeout", result.getStatusReason());
    }

    private Device device(int id, String number) {
        Device device = new Device();
        device.setId(id);
        device.setNumber(number);
        return device;
    }

    private RemoteScreenFrame frame() {
        RemoteScreenFrame frame = new RemoteScreenFrame();
        frame.setImageData("data:image/jpeg;base64,AA==");
        frame.setWidth(1);
        frame.setHeight(1);
        return frame;
    }

    private RemoteScreenControl control(String type) {
        RemoteScreenControl control = new RemoteScreenControl();
        control.setType(type);
        return control;
    }

    private static class NoopPushSender implements PushSender {
        @Override
        public void init() {
        }

        @Override
        public int send(PushMessage message) {
            return 0;
        }
    }

    private static class RecordingPushSender extends NoopPushSender {
        private final List<PushMessage> messages = new ArrayList<>();

        @Override
        public int send(PushMessage message) {
            messages.add(message);
            return 0;
        }
    }

    private static class RecordingNotificationMapper implements NotificationMapper {
        private int deviceId;
        private int deleteCallCount;
        private List<String> messageTypes;

        @Override
        public List<PushMessage> getPendingMessagesByNumber(String deviceNumber) {
            return null;
        }

        @Override
        public List<PushMessage> getPendingMessagesById(int deviceId) {
            return null;
        }

        @Override
        public void markMessagesAsDelivered(List<Integer> messageIds) {
        }

        @Override
        public void insertPushMessage(PushMessage message) {
        }

        @Override
        public void insertPendingPush(int messageId) {
        }

        @Override
        public Integer getDeliveryStatus(int messageId) {
            return null;
        }

        @Override
        public void purgeMessages(long nonDeliveredMessagesLifeSpan, long deliveredMessagesLifeSpan) {
        }

        @Override
        public void deletePendingMessages(int deviceId, List<String> messageTypes) {
            this.deviceId = deviceId;
            this.messageTypes = messageTypes;
            this.deleteCallCount++;
        }
    }
}
