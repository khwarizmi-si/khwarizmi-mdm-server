package com.hmdm.rest.resource;

import com.google.inject.name.Named;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.RemoteScreenFrame;
import com.hmdm.rest.json.RemoteScreenSession;
import com.hmdm.rest.json.RemoteScreenStatus;
import com.hmdm.rest.json.Response;
import com.hmdm.service.RemoteScreenSessionService;
import com.hmdm.util.CryptoUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(tags = {"Remote screen public"}, authorizations = {@Authorization("Device signature")})
@Singleton
@Path("/public/remote-screen")
public class RemoteScreenPublicResource {
    private static final Logger log = LoggerFactory.getLogger(RemoteScreenPublicResource.class);
    private static final String SIGNATURE_HEADER = "X-Request-Signature";

    private final UnsecureDAO unsecureDAO;
    private final RemoteScreenSessionService sessionService;
    private final String hashSecret;
    private final boolean secureEnrollment;

    @Inject
    public RemoteScreenPublicResource(UnsecureDAO unsecureDAO,
                                      RemoteScreenSessionService sessionService,
                                      @Named("hash.secret") String hashSecret,
                                      @Named("secure.enrollment") boolean secureEnrollment) {
        this.unsecureDAO = unsecureDAO;
        this.sessionService = sessionService;
        this.hashSecret = hashSecret;
        this.secureEnrollment = secureEnrollment;
    }

    @ApiOperation(value = "Upload a remote screen frame")
    @POST
    @Path("/{deviceNumber}/sessions/{sessionId}/frame")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFrame(@PathParam("deviceNumber") String deviceNumber,
                                @PathParam("sessionId") String sessionId,
                                @HeaderParam(SIGNATURE_HEADER) String signature,
                                RemoteScreenFrame frame) {
        RemoteScreenSession session = getAuthorizedSession(deviceNumber, sessionId, signature);
        if (session == null) {
            return Response.ERROR("error.remote.screen.session.not.found");
        }

        if (frame == null) {
            return Response.ERROR();
        }
        frame.setSessionId(sessionId);
        session = sessionService.updateFrame(sessionId, frame);
        if (session == null) {
            return Response.ERROR();
        }
        return Response.OK();
    }

    @ApiOperation(value = "Update remote screen session status")
    @POST
    @Path("/{deviceNumber}/sessions/{sessionId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateStatus(@PathParam("deviceNumber") String deviceNumber,
                                 @PathParam("sessionId") String sessionId,
                                 @HeaderParam(SIGNATURE_HEADER) String signature,
                                 RemoteScreenStatus status) {
        RemoteScreenSession session = getAuthorizedSession(deviceNumber, sessionId, signature);
        if (session == null || status == null) {
            return Response.ERROR("error.remote.screen.session.not.found");
        }
        session = sessionService.updateStatus(sessionId, status.getStatus(), status.getReason());
        return session != null ? Response.OK() : Response.ERROR();
    }

    private RemoteScreenSession getAuthorizedSession(String deviceNumber, String sessionId, String signature) {
        if (secureEnrollment && !CryptoUtil.checkRequestSignature(signature, hashSecret + deviceNumber)) {
            log.warn("Remote screen request rejected for device {}: signature mismatch", deviceNumber);
            return null;
        }

        Device device = unsecureDAO.getDeviceByNumber(deviceNumber);
        if (device == null) {
            device = unsecureDAO.getDeviceByOldNumber(deviceNumber);
        }
        if (device == null) {
            return null;
        }

        RemoteScreenSession session = sessionService.get(sessionId);
        if (session == null || session.getDeviceId() != device.getId()) {
            return null;
        }
        return session;
    }
}
