/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.rest.auth.PrivilegedServiceToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Transactional;
import com.google.common.net.HttpHeaders;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.aerofs.auth.client.shared.AeroService.*;
import static com.aerofs.sp.authentication.DeploymentSecret.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Path(Service.VERSION + "/devices")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class DevicesResource extends AbstractSpartaResource
{
    private static Logger l = LoggerFactory.getLogger(DevicesResource.class);

    private final User.Factory _factUser;

    private final Organization.Factory _factOrg;

    // This value must be larger than the charlie check-in interval.
    public static final Integer ALLOWED_OFFLINE_SECONDS = 300;

    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final ThreadLocal<DateFormat> _dateFormat = new ThreadLocal<DateFormat>() {
        @Override
        public DateFormat initialValue() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f;
        }
    };

    @Inject
    public DevicesResource(User.Factory factUser, Organization.Factory factOrg)
    {
        _factUser = factUser;
        _factOrg = factOrg;
    }

    private @Nullable User validateAuth(IAuthToken token, Scope scope, User user)
            throws SQLException, ExNotFound
    {
        requirePermission(scope, token);
        if (token instanceof IUserAuthToken) {
            User caller = _factUser.create(((IUserAuthToken)token).user());
            throwIfNotSelfOrTSOf(caller, user);
            return caller;
        }
        return null;
    }

    private static void throwIfNotSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        if (!UserManagement.isSelfOrTSOf(caller, target)) throw new ExNotFound("No such device");
    }

    @Since("1.3")
    @GET
    @Path("/{did}")
    public Response get(@Auth IAuthToken token, @PathParam("did") Device d)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.READ_USER, d.getOwner());

        return Response.ok()
                .entity(new com.aerofs.rest.api.Device(d.id().toStringFormal(),
                        d.getOwner().id().getString(), d.getName(), d.getOSFamily(),
                        new Date(d.getInstallDate())))
                .build();
    }

    @Since("1.3")
    @PUT
    @Path("/{did}")
    public Response update(@Auth IAuthToken token, @PathParam("did") Device d,
                           com.aerofs.rest.api.Device attrs)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.WRITE_USER, d.getOwner());

        checkArgument(attrs.name != null, "Request body missing required field: name");

        d.setName(attrs.name);
        return Response.ok()
                .entity(new com.aerofs.rest.api.Device(d.id().toStringFormal(),
                        d.getOwner().id().getString(), d.getName(), d.getOSFamily(),
                        new Date(d.getInstallDate())))
                .build();
    }

    private static class OnlinePayload
    {
        public String ip;
        public String time;
    }

    @Since("1.3")
    @GET
    @Path("/{did}/status")
    public Response status(@Auth IAuthToken token, @PathParam("did") Device d)
            throws ExNotFound, SQLException
    {
        validateAuth(token, Scope.READ_USER, d.getOwner());

        try {
            HttpURLConnection conn = (HttpURLConnection)(
                    new URL("http://charlie.service:8701/checkin/" + d.id().toStringFormal())).openConnection();
            conn.addRequestProperty(HttpHeaders.AUTHORIZATION, getHeaderValue("sparta", getSecret()));
            conn.setConnectTimeout((int) TimeUnit.MILLISECONDS.convert(5L, TimeUnit.SECONDS));
            conn.connect();
            checkState(conn.getResponseCode() == 200);

            boolean online;
            Date lastSeen;
            try (Reader r = new InputStreamReader(conn.getInputStream())) {
                OnlinePayload payload = _gson.fromJson(r, OnlinePayload.class);
                lastSeen = _dateFormat.get().parse(payload.time);
                long checkinDiffSeconds = (System.currentTimeMillis() - lastSeen.getTime()) / 1000;
                online = checkinDiffSeconds <= ALLOWED_OFFLINE_SECONDS;
            }
            return Response.ok()
                    .entity(new com.aerofs.rest.api.DeviceStatus(online, lastSeen))
                    .build();
        } catch (Exception e) {
            l.error("Error getting device status: {}", e);
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Retrieves a count of number of devices in the org
     *
     * @param deviceOS specifies the type of devices to get a count for.
     */
    @Since("1.4")
    @GET
    @Path("/count")
    public Response count(@Auth PrivilegedServiceToken token, @QueryParam("device_os") String deviceOS)
            throws ExNotFound, SQLException, ExInvalidID
    {
        checkArgument((deviceOS.equals("Windows") || deviceOS.equals("Mac OS X") || deviceOS.equals("Linux") ||
                deviceOS.equals("iOS") || deviceOS.equals("Android")), "Invalid query parameter specified");

        int count = 0;
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        if (deviceOS.equals("Windows") || deviceOS.equals("Mac OS X") || deviceOS.equals("Linux"))
        {
            count = org.countClientDevicesByOS(deviceOS);
        }
        else if (deviceOS.equals("iOS") || deviceOS.equals("Android")) {
            count = org.countMobileDevicesByOS(deviceOS);
        }

        return Response.ok()
                .entity(count)
                .build();
    }

    /**
     * Retrieves a count of the number of team servers in the org
     */
    @Since("1.4")
    @GET
    @Path("/count/teamservers")
    public Response countStorageAgents(@Auth PrivilegedServiceToken token)
            throws ExNotFound, SQLException, ExInvalidID
    {
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);

        return Response.ok()
                .entity(org.countTeamServers())
                .build();
    }
}
