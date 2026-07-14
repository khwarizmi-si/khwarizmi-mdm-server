package com.hmdm.service;

import com.google.inject.Singleton;
import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.domain.Device;
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
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ENDED = "ended";

    private final PushService pushService;
    private final Map<String, RemoteScreenSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public RemoteScreenSessionService(PushService pushService) {
        this.pushService = pushService;
    }

    public RemoteScreenSession start(Device device) {
        long now = System.currentTimeMillis();
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

    public RemoteScreenSession get(String id) {
        RemoteScreenSession session = sessions.get(id);
        if (session == null) {
            return null;
        }
        if (session.getExpiresAt() < System.currentTimeMillis() && !STATUS_ENDED.equals(session.getStatus())) {
            session.setStatus("expired");
            session.setUpdatedAt(System.currentTimeMillis());
        }
        return session;
    }

    public RemoteScreenSession stop(String id) {
        RemoteScreenSession session = get(id);
        if (session == null) {
            return null;
        }
        session.setStatus(STATUS_ENDED);
        session.setUpdatedAt(System.currentTimeMillis());

        PushMessage message = new PushMessage();
        message.setDeviceId(session.getDeviceId());
        message.setMessageType(PushMessage.TYPE_REMOTE_SCREEN_STOP);
        message.setPayload("{\"sessionId\":\"" + StringUtil.jsonEscape(session.getId()) + "\"}");
        pushService.send(message);

        log.info("Remote screen session {} stopped by user {}", id,
                SecurityContext.get().getCurrentUser().get().getId());
        return session;
    }
}
