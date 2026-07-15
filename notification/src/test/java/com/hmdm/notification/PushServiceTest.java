package com.hmdm.notification;

import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PushServiceTest {

    @Test
    public void clearPendingRemoteScreenMessagesDeletesOnlyRemoteScreenPushTypes() {
        RecordingNotificationMapper mapper = new RecordingNotificationMapper();
        PushService service = new PushService(new NoopPushSender(), new NoopPushSender(),
                null, null, mapper);

        service.clearPendingRemoteScreenMessages(42);

        assertEquals(42, mapper.deviceId);
        assertEquals(3, mapper.messageTypes.size());
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_START, mapper.messageTypes.get(0));
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_STOP, mapper.messageTypes.get(1));
        assertEquals(PushMessage.TYPE_REMOTE_SCREEN_CONTROL, mapper.messageTypes.get(2));
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

    private static class RecordingNotificationMapper implements NotificationMapper {
        private int deviceId;
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
        }
    }
}
