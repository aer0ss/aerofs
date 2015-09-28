package com.aerofs.trifrost.resources;

import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.UnifiedPushConfiguration;
import com.aerofs.trifrost.api.*;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.base.InvalidCodeException;
import com.aerofs.trifrost.base.UniqueID;
import com.aerofs.trifrost.base.UniqueIDGenerator;
import com.aerofs.trifrost.db.*;
import com.aerofs.trifrost.model.AuthorizedUser;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Date;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Resource for signing and registering a device for push notification.
 */
@Path("/auth")
@PermitAll
public final class AuthResource {
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);
    private static final Device DEFAULT_DEVICE = new Device("", "");
    private final DBI dbi;
    private final AbstractEmailSender mailSender;

    private UniqueIDGenerator uniq;

    private final String MAIL_FROM_NAME = getStringProperty("labeling.brand", "AeroFS");
    private final String MAIL_FROM_ADDR = getStringProperty("base.www.support_email_address", "");


    public AuthResource(@Context DBI dbi,
                        @Context AbstractEmailSender mailSender,
                        @Context UnifiedPushConfiguration unifiedPushConfiguration,
                        @Context UniqueIDGenerator uniqueID) throws IOException {
        this.dbi = dbi;
        this.mailSender = mailSender;
        this.uniq = uniqueID;
    }

    /**
     * Request a verification code be sent to the given email address.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth_code")
    public Response requestVerification(final EmailAddress claimedEmail) {
        return dbi.inTransaction((conn, status) -> {
            VerificationCodes codes = conn.attach(VerificationCodes.class);

            char[] authCodeCh = uniq.generateOneTimeCode();
            String authCode = new String(authCodeCh);

            codes.add(claimedEmail.getEmail(), authCode, new Date().getTime());

            mailSender.sendPublicEmail(MAIL_FROM_ADDR, MAIL_FROM_NAME,
                    claimedEmail.getEmail(),
                    null,
                    "Your AeroIM code is " + authCode,
                    "Here be two snowmans: ☃ ☃",
                    null);

            logger.info("verification code {} for user {}", authCode, claimedEmail.getEmail());
            return Response.ok().build();
        });
    }

    /**
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/token")
    public VerifiedDevice verifyDevice(final DeviceAuthentication auth) {

        if (auth.grantType == null) {
            throw new BadRequestException("grant type must be specified");
        }

        if (auth.grantType == DeviceAuthentication.GrantType.RefreshToken) {
            if (Strings.isNullOrEmpty(auth.refreshToken) || Strings.isNullOrEmpty(auth.userId)) {
                throw new BadRequestException("error processing refresh token request");
            }
            return handleRefreshToken(auth);
        }

        assert auth.grantType == DeviceAuthentication.GrantType.AuthCode;
        if (Strings.isNullOrEmpty(auth.email)) {
            throw new BadRequestException("missing required field \"email\"");
        } else if (Strings.isNullOrEmpty(auth.authCode)) {
            throw new BadRequestException("missing required field \"auth_code\"");
        }
        return handleAuthCode(auth);
    }

    // clear all auth tokens associated with this refresh token
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/refresh")
    public Response invalidateRefresh(
            @Context AuthorizedUser authorizedUser,
            RefreshToken provided)
    {

        Preconditions.checkNotNull(authorizedUser);

        return dbi.inTransaction((conn, status) -> {
            int rows = conn.attach(RefreshTokens.class).invalidate(provided.refreshToken, authorizedUser.id);

            if (rows > 0) {
                return Response.noContent().build();
            } else {
                throw new ForbiddenException("Error invalidating refresh token");
            }
        });
    }

    private VerifiedDevice handleRefreshToken(final DeviceAuthentication auth) {
        return dbi.inTransaction((conn, status) -> {
            boolean isValid = conn.attach(RefreshTokens.class).invalidate(auth.refreshToken, auth.userId) > 0;
            if (isValid) {

                String refreshToken = new String(UniqueID.generateUUID());
                String authToken = new String(UniqueID.generateUUID());
                long authExpiry = UniqueID.getDefaultTokenExpiry();
                conn.attach(RefreshTokens.class).add(refreshToken, auth.userId, UniqueID.getDefaultRefreshExpiry());
                conn.attach(AuthTokens.class).add(authToken, refreshToken, UniqueID.getDefaultTokenExpiry());

                return new VerifiedDevice(auth.userId, Constants.AERO_IM, "", authToken, authExpiry, refreshToken);
            } else {
                throw new ForbiddenException("Found a problem with your refresh token");
            }
        });

    }

    private VerifiedDevice handleAuthCode(final DeviceAuthentication auth) {
        return dbi.inTransaction((conn, status) -> {
            VerificationCodes codes = conn.attach(VerificationCodes.class);

            if (codes.consumeVerification(auth.email, auth.authCode) == 0) {
                logger.info("verify failed for {} : {}", auth.email, auth.authCode);
                throw new InvalidCodeException();
            }

            // save refresh token
            String refreshToken = new String(UniqueID.generateUUID());
            UserAddresses addresses = conn.attach(UserAddresses.class);

            String userId1 = addresses.get(auth.email);
            if (userId1 == null) {
                userId1 = new String(UniqueID.generateUUID());
                addresses.add(auth.email, userId1);
            }
            String userId = userId1;
            conn.attach(RefreshTokens.class).add(refreshToken, userId, UniqueID.getDefaultRefreshExpiry());

            // save auth token
            String authToken = new String(UniqueID.generateUUID());
            long authExpiry = new Date().getTime() + 30L * 86400 * 1000;
            conn.attach(AuthTokens.class).add(authToken, refreshToken, UniqueID.getDefaultTokenExpiry());

            // save device, if provided
            Device device = auth.device == null ? DEFAULT_DEVICE : auth.device;
            String deviceId = new String(uniq.generateDeviceString());
            conn.attach(Devices.class).add(deviceId, userId, device);

            return new VerifiedDevice(userId, Constants.AERO_IM, deviceId, authToken, authExpiry, refreshToken);
        });
    }
}
