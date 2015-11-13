package com.aerofs.trifrost.resources;

import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.ISpartaClient;
import com.aerofs.trifrost.api.*;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.base.InvalidCodeException;
import com.aerofs.trifrost.base.UniqueID;
import com.aerofs.trifrost.base.UniqueIDGenerator;
import com.aerofs.trifrost.db.*;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

@Path("/auth")
@PermitAll
@Api(value = "user authorization",
        produces = "application/json",
        consumes = "application/json")
public final class AuthResource {
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);
    private static final Device DEFAULT_DEVICE = new Device("", "");
    private final DBI dbi;
    private final AbstractEmailSender mailSender;
    private UniqueIDGenerator uniq;
    private ISpartaClient sparta;

    private final String MAIL_FROM_NAME = getStringProperty("labeling.brand", "AeroFS");
    private final String MAIL_FROM_ADDR = getStringProperty("base.www.support_email_address", "");

    public AuthResource(@Context DBI dbi,
                        @Context AbstractEmailSender mailSender,
                        @Context UniqueIDGenerator uniqueID,
                        @Context ISpartaClient sparta) {
        this.dbi = dbi;
        this.mailSender = mailSender;
        this.uniq = uniqueID;
        this.sparta = sparta;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth_code")
    @ApiOperation(
            value = "Request a verification code by email",
            notes = "Initiate the sign-in process by requesting a unique verification code by email"
    )
    public Response requestVerification(@NotNull final EmailAddress claimedEmail) {
        return dbi.inTransaction((conn, status) -> {
            VerificationCodes codes = conn.attach(VerificationCodes.class);

            char[] authCodeCh = uniq.generateOneTimeCode();
            String authCode = new String(authCodeCh);

            codes.add(claimedEmail.email, authCode, new Date().getTime());

            mailSender.sendPublicEmail(MAIL_FROM_ADDR, MAIL_FROM_NAME,
                    claimedEmail.email,
                    null,
                    "Your AeroIM code is " + authCode,
                    "Here be two snowmans: ☃ ☃",
                    null);

            logger.info("verification code {} for user {}", authCode, claimedEmail.email);
            return Response.ok().build();
        });
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/token")
    @ApiOperation(
            value = "Create a new access token",
            notes = "Complete the sign-in process and create a new access token for the given user.\n\n" +
                    "This endpoint supports two mechanisms to prove identity; .\n\n" +
                    "The `grant_type` body parameter is required, and chooses between the two supported " +
                    "identity-verification mechanisms: a single-use, emailed auth code (" +
                    "created by the `auth/auth_code` route) or a previously-issued refresh token.\n\n" +
                    "On successful verification, an access token and refresh token will be issued."
    )
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

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/refresh")
    @ApiOperation(value = "clear all auth tokens",
            notes = "Invalidate the given refresh tokens as well as any access tokens that were issued with it."
    )
    public Response invalidateTokens(
            @Context AuthorizedUser authorizedUser,
            RefreshToken provided) {
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

            // FIXME: why are we generating userId's? Just for devices. Can we remove it all??
            String userId = Users.get(conn, auth.email);
            if (userId == null) {
                userId = Users.allocate(conn, auth.email);
            }

            // save device, if provided
            // FIXME: remove this chunk?
            Device device = auth.device == null ? DEFAULT_DEVICE : auth.device;
            String deviceId = new String(uniq.generateDeviceString());
            conn.attach(Devices.class).add(deviceId, userId, device);


            // Get a bearer token from bifrost, now that we have authenticated the request.
            VerifiedDevice retVal = sparta.getTokenForUser(auth.email);
            retVal.domain = Constants.AERO_IM;
            retVal.deviceId = deviceId;
            return retVal;
        });
    }
}
