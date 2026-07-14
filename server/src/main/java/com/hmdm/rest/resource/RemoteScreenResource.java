package com.hmdm.rest.resource;

import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.RemoteScreenControl;
import com.hmdm.rest.json.RemoteScreenSession;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import com.hmdm.security.SecurityException;
import com.hmdm.service.RemoteScreenSessionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(tags = {"Remote screen"}, authorizations = {@Authorization("Bearer Token")})
@Singleton
@Path("/private/remote-screen")
public class RemoteScreenResource {
    private static final Logger log = LoggerFactory.getLogger(RemoteScreenResource.class);

    private DeviceDAO deviceDAO;
    private RemoteScreenSessionService sessionService;

    public RemoteScreenResource() {
    }

    @Inject
    public RemoteScreenResource(DeviceDAO deviceDAO, RemoteScreenSessionService sessionService) {
        this.deviceDAO = deviceDAO;
        this.sessionService = sessionService;
    }

    @ApiOperation(value = "Start a remote screen session", authorizations = {@Authorization("Bearer Token")})
    @POST
    @Path("/{deviceId}/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("deviceId") @ApiParam("Device ID") Integer deviceId) {
        try {
            if (!SecurityContext.get().hasPermission("remote_screen_view")) {
                log.error("Unauthorized remote screen attempt for device #{}",
                        deviceId, SecurityException.onCustomerDataAccessViolation(deviceId, "device"));
                return Response.PERMISSION_DENIED();
            }
            Device device = deviceDAO.getDeviceById(deviceId);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            return Response.OK(sessionService.start(device));
        } catch (Exception e) {
            log.error("Failed to start remote screen session for device #{}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(value = "Get remote screen session status", authorizations = {@Authorization("Bearer Token")})
    @GET
    @Path("/sessions/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("sessionId") String sessionId) {
        if (!SecurityContext.get().hasPermission("remote_screen_view")) {
            return Response.PERMISSION_DENIED();
        }
        RemoteScreenSession session = sessionService.get(sessionId);
        if (session == null) {
            return Response.ERROR("error.remote.screen.session.not.found");
        }
        if (!canAccess(session)) {
            return Response.PERMISSION_DENIED();
        }
        return Response.OK(session);
    }

    @ApiOperation(value = "Stop a remote screen session", authorizations = {@Authorization("Bearer Token")})
    @POST
    @Path("/sessions/{sessionId}/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop(@PathParam("sessionId") String sessionId) {
        try {
            if (!SecurityContext.get().hasPermission("remote_screen_view")) {
                return Response.PERMISSION_DENIED();
            }
            RemoteScreenSession session = sessionService.get(sessionId);
            if (session == null) {
                return Response.ERROR("error.remote.screen.session.not.found");
            }
            if (!canAccess(session)) {
                return Response.PERMISSION_DENIED();
            }
            session = sessionService.stop(sessionId);
            return Response.OK(session);
        } catch (Exception e) {
            log.error("Failed to stop remote screen session {}", sessionId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(value = "Send a remote screen control action", authorizations = {@Authorization("Bearer Token")})
    @POST
    @Path("/sessions/{sessionId}/control")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response control(@PathParam("sessionId") String sessionId, RemoteScreenControl control) {
        try {
            if (!SecurityContext.get().hasPermission("remote_screen_view")) {
                return Response.PERMISSION_DENIED();
            }
            RemoteScreenSession session = sessionService.get(sessionId);
            if (session == null) {
                return Response.ERROR("error.remote.screen.session.not.found");
            }
            if (!canAccess(session)) {
                return Response.PERMISSION_DENIED();
            }
            session = sessionService.control(sessionId, control);
            if (session == null) {
                return Response.ERROR("error.remote.screen.control.invalid");
            }
            return Response.OK(session);
        } catch (Exception e) {
            log.error("Failed to control remote screen session {}", sessionId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    private boolean canAccess(RemoteScreenSession session) {
        return SecurityContext.get().isSuperAdmin() ||
                session.getAdminUserId() == SecurityContext.get().getCurrentUser().get().getId();
    }
}
