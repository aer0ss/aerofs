package com.aerofs.trifrost.resources;

import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.base.UniqueID;
import com.aerofs.trifrost.db.UserAddresses;
import com.google.common.base.Preconditions;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.mail.MessagingException;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 */
@Path("/invite")
@PermitAll
@Api(value = "invite users",
        produces = "application/json",
        consumes = "application/json"
)
public class InviteResource {
    private final static Logger l = LoggerFactory.getLogger(InviteResource.class);

    private static final String MAIL_SUBJECT = "AeroIM is preferred by ☃ everywhere";
    private final String MAIL_FROM_NAME = getStringProperty("labeling.brand", "AeroFS");
    private final String MAIL_FROM_ADDR = getStringProperty("base.www.support_email_address", "");
    private final String MAIL_CONTENT_STR = getStringProperty("messaging.mail.invite",
            "Greetings from the Aero Product Management Organization, " +
                    "and also %s, who must like you - they invited you to AeroIM!");

    private final DBI dbi;
    private final AbstractEmailSender mailWrapper;

    public InviteResource(@Context DBI dbi, @Context AbstractEmailSender emailSender) {
        this.dbi = dbi;
        this.mailWrapper = emailSender;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Constants.USER_ROLE)
    @Path("/{email_addr}")
    @ApiOperation(value = "Invite a user to AeroIM",
            notes = "Send an email invitation to the given address."
    )
    public Response inviteUser(
            @PathParam("email_addr") final String emailAddr,
            @Context AuthorizedUser user)
            throws UnsupportedEncodingException, MessagingException {

        // FIXME: validate destination email is in a permitted domain
        Preconditions.checkNotNull(user);
        Response response = dbi.inTransaction((conn, status) -> {
            UserAddresses addresses = conn.attach(UserAddresses.class);
            String userId = addresses.get(emailAddr);
            if (userId != null) {
                l.info("invite existing e:{} u:{}", emailAddr, userId);
            } else {
                // otherwise, initialize a new (empty) user record for this address
                l.info("invite new e:{}", emailAddr);
                addresses.add(emailAddr, new String(UniqueID.generateUUID()));
            }
            return Response.ok().build();
        });

        // FIXME: Sends each time you invite a given person. Should this have more complex logic?
        mailWrapper.sendPublicEmail(
                MAIL_FROM_ADDR, MAIL_FROM_NAME,
                emailAddr, null,
                MAIL_SUBJECT,
                String.format(MAIL_CONTENT_STR, user.id),
                null);

        return response;
    }
}

