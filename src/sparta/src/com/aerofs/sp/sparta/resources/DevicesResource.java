/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.UserManagement;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Transactional;
import com.aerofs.verkehr.api.rest.Update;
import com.aerofs.verkehr.client.rest.VerkehrClient;
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
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkArgument;

@Path(Service.VERSION + "/devices")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class DevicesResource extends AbstractSpartaResource
{
    private static Logger l = LoggerFactory.getLogger(DevicesResource.class);

    private final VerkehrClient _vk;
    private final User.Factory _factUser;

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
    public DevicesResource(VerkehrClient vk, User.Factory factUser)
    {
        _vk = vk;
        _factUser = factUser;
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
            List<Update> l = _vk.getUpdates("online%2F" + d.id().toStringFormal(), -1).get().getUpdates();
            boolean online = false;
            Date lastSeen = null;
            if (l.size() == 1) {
                String s = new String(l.get(0).getPayload(), StandardCharsets.UTF_8);
                OnlinePayload payload = _gson.fromJson(s, OnlinePayload.class);
                lastSeen = _dateFormat.get().parse(payload.time);
                long checkinDiffSeconds = (System.currentTimeMillis() - lastSeen.getTime()) / 1000;
                online = checkinDiffSeconds <= ALLOWED_OFFLINE_SECONDS;
            }
            return Response.ok()
                    .entity(new com.aerofs.rest.api.DeviceStatus(online, lastSeen))
                    .build();
        } catch (InterruptedException|ExecutionException|ParseException e) {
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }
    }
}
