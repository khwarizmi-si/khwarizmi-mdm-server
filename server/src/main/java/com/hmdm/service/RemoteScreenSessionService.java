package com.hmdm.service;

import com.google.inject.Singleton;
import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.RemoteScreenControl;
import com.hmdm.rest.json.RemoteScreenFrame;
import com.hmdm.rest.json.RemoteScreenSession;
import com.hmdm.security.SecurityContext;
import com.hmdm.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class RemoteScreenSessionService {
    private static final Logger log = LoggerFactory.getLogger(RemoteScreenSessionService.class);
    private static final long SESSION_TTL_MS = 5 * 60 * 1000L;
    private static final long FRAME_TIMEOUT_MS = 15 * 1000L;
    private static final int MAX_FRAME_LENGTH = 2 * 1024 * 1024;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ENDED = "ended";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_FAILED = "failed";

    private final PushService pushService;
    private final Map<String, RemoteScreenSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public RemoteScreenSessionService(PushService pushService) {
        this.pushService = pushService;
    }

    public RemoteScreenSession start(Device device) {
        long now = System.currentTimeMillis();
        pushService.clearPendingRemoteScreenMessages(device.getId());
        closeOpenSessions(device.getId(), now);

        RemoteScreenSession session = new RemoteScreenSession();
        session.setId(UUID.randomUUID().toString());
        session.setDeviceId(device.getId());
        session.setDeviceNumber(device.getNumber());
        session.setAdminUserId(SecurityContext.get().getCurrentUser().get().getId());
        session.setStatus(STATUS_PENDING);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now + SESSION_TTL_MS);
        sessions.put(session.getId(), session);

        PushMessage message = new PushMessage();
        message.setDeviceId(device.getId());
        message.setMessageType(PushMessage.TYPE_REMOTE_SCREEN_START);
        message.setPayload("{\"sessionId\":\"" + StringUtil.jsonEscape(session.getId()) + "\"}");
        pushService.send(message);

        log.info("Remote screen session {} requested for device {} by user {}",
                session.getId(), device.getId(), session.getAdminUserId());
        return session;
    }

    private void closeOpenSessions(int deviceId, long now) {
        sessions.values().stream()
                .filter(session -> session.getDeviceId() == deviceId)
                .filter(session -> !STATUS_ENDED.equals(session.getStatus()))
                .filter(session -> !STATUS_EXPIRED.equals(session.getStatus()))
                .filter(session -> !STATUS_FAILED.equals(session.getStatus()))
                .forEach(session -> {
                    session.setStatus(STATUS_ENDED);
                    session.setUpdatedAt(now);
                    sendStopMessage(session);
                });
    }

    public RemoteScreenSession get(String id) {
        RemoteScreenSession session = sessions.get(id);
        if (session == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (session.getExpiresAt() < now &&
                !STATUS_ENDED.equals(session.getStatus()) && !STATUS_FAILED.equals(session.getStatus())) {
            session.setStatus(STATUS_EXPIRED);
            session.setUpdatedAt(now);
        } else if (STATUS_ACTIVE.equals(session.getStatus()) &&
                session.getFrameUpdatedAt() + FRAME_TIMEOUT_MS < now) {
            session.setStatus(STATUS_FAILED);
            session.setStatusReason("frame_timeout");
            session.setUpdatedAt(now);
        }
        return session;
    }

    public RemoteScreenSession updateFrame(String sessionId, RemoteScreenFrame frame) {
        RemoteScreenSession session = get(sessionId);
        if (session == null || STATUS_ENDED.equals(session.getStatus()) ||
                STATUS_EXPIRED.equals(session.getStatus()) || STATUS_FAILED.equals(session.getStatus())) {
            return null;
        }
        String imageData = frame.getImageData();
        if (imageData == null || imageData.length() > MAX_FRAME_LENGTH ||
                !imageData.startsWith("data:image/jpeg;base64,")) {
            return null;
        }
        long now = System.currentTimeMillis();
        session.setStatus(STATUS_ACTIVE);
        session.setStatusReason(null);
        session.setUpdatedAt(now);
        session.setFrameUpdatedAt(now);
        session.setFrameDataUrl(imageData);
        return session;
    }

    public RemoteScreenSession updateStatus(String sessionId, String status, String reason) {
        RemoteScreenSession session = get(sessionId);
        if (session == null || STATUS_EXPIRED.equals(session.getStatus())) {
            return null;
        }
        if (!STATUS_FAILED.equals(status) && !STATUS_ENDED.equals(status)) {
            return null;
        }
        session.setStatus(status);
        session.setStatusReason(reason);
        session.setUpdatedAt(System.currentTimeMillis());
        return session;
    }

    public RemoteScreenSession control(String id, RemoteScreenControl control) {
        RemoteScreenSession session = get(id);
        if (session == null || STATUS_ENDED.equals(session.getStatus()) ||
                STATUS_EXPIRED.equals(session.getStatus()) || STATUS_FAILED.equals(session.getStatus())) {
            return null;
        }
        if (!isValidControl(control)) {
            return null;
        }

        PushMessage message = new PushMessage();
        message.setDeviceId(session.getDeviceId());
        message.setMessageType(PushMessage.TYPE_REMOTE_SCREEN_CONTROL);
        message.setPayload(toPayload(session, control));
        pushService.send(message);

        session.setUpdatedAt(System.currentTimeMillis());
        return session;
    }

    public RemoteScreenSession stop(String id) {
        RemoteScreenSession session = get(id);
        if (session == null) {
            return null;
        }
        session.setStatus(STATUS_ENDED);
        session.setUpdatedAt(System.currentTimeMillis());
        pushService.clearPendingRemoteScreenMessages(session.getDeviceId());

        sendStopMessage(session);

        log.info("Remote screen session {} stopped by user {}", id,
                SecurityContext.get().getCurrentUser().get().getId());
        return session;
    }

    private void sendStopMessage(RemoteScreenSession session) {
        PushMessage message = new PushMessage();
        message.setDeviceId(session.getDeviceId());
        message.setMessageType(PushMessage.TYPE_REMOTE_SCREEN_STOP);
        message.setPayload("{\"sessionId\":\"" + StringUtil.jsonEscape(session.getId()) + "\"}");
        pushService.send(message);
    }

    private boolean isNormalized(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0 && value <= 1;
    }

    private boolean isValidControl(RemoteScreenControl control) {
        if (control == null || control.getType() == null) {
            return false;
        }
        if ("back".equals(control.getType()) || "home".equals(control.getType()) || "recents".equals(control.getType())) {
            return true;
        }
        if ("tap".equals(control.getType())) {
            return isNormalized(control.getX()) && isNormalized(control.getY());
        }
        return "swipe".equals(control.getType()) &&
                isNormalized(control.getX()) && isNormalized(control.getY()) &&
                isNormalized(control.getX2()) && isNormalized(control.getY2());
    }

    private String toPayload(RemoteScreenSession session, RemoteScreenControl control) {
        StringBuilder payload = new StringBuilder("{\"sessionId\":\"")
                .append(StringUtil.jsonEscape(session.getId()))
                .append("\",\"type\":\"")
                .append(control.getType())
                .append("\"");
        if ("tap".equals(control.getType()) || "swipe".equals(control.getType())) {
            payload.append(",\"x\":").append(control.getX())
                    .append(",\"y\":").append(control.getY());
        }
        if ("swipe".equals(control.getType())) {
            payload.append(",\"x2\":").append(control.getX2())
                    .append(",\"y2\":").append(control.getY2());
        }
        return payload.append("}").toString();
    }
}
