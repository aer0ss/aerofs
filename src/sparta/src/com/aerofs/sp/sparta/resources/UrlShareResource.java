package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.SID;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.restless.Version;
import com.aerofs.sp.server.AccessCodeProvider;
import com.aerofs.sp.server.Zelda;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.sp.sparta.Transactional;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Date;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

@Path(Service.VERSION + "/links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class UrlShareResource extends AbstractSpartaResource
{
    public static final String X_REAL_IP = "X-Real-IP";
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private final User.Factory _factUser;
    private final SharedFolder.Factory _factSF;
    private final UrlShare.Factory _factUrlShare;
    private final AuditClient _auditClient;
    private final AccessCodeProvider _accessCodeProvider;
    private final Zelda _zelda;
    private final TimeSource _timeSource;

    @Inject
    public UrlShareResource(
            User.Factory factUser,
            SharedFolder.Factory factSF,
            UrlShare.Factory factUrlShare,
            AuditClient auditClient,
            AccessCodeProvider accessCodeProvider,
            Zelda zelda,
            TimeSource timeSource)
    {
        _factUser = factUser;
        _factSF = factSF;
        _factUrlShare = factUrlShare;
        _auditClient = auditClient;
        _accessCodeProvider = accessCodeProvider;
        _zelda = zelda;
        _timeSource = timeSource;
    }

    @Since("1.4")
    @GET
    @Path("/{key}")
    public Response getURLInfo(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.READ_ACL, urlShare);

        createAuditEvent(token, ip, "link.access", urlShare).publish();

        return Response.ok()
                .entity(toUrlShareResponse(urlShare))
                .build();
    }

    @Since("1.4")
    @POST
    public Response createURL(@Auth IUserAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @Context Version version,
            com.aerofs.rest.api.UrlShare request)
            throws ExBadArgs, ExNoPerm, ExNotFound,
            IOException, GeneralSecurityException, SQLException, URISyntaxException
    {
        RestObject restObject = RestObject.fromString(request.soid);

        // the only reason to not use checkNotNull is that checkNotNull throws
        // NullPointerException instead of IllegalArgumentException
        checkArgument(restObject.getSID() != null);
        checkArgument(restObject.getOID() != null);

        // follow anchors because creating a link to a anchor isn't very useful
        //
        // the caller must have obtained an anchor by traversing their own root store and
        // intended to create a link to the shared folder instead of the anchor.
        if (restObject.getOID().isAnchor()) {
            restObject = new RestObject(SID.anchorOID2storeSID(restObject.getOID()));
        }

        // N.B. we _must_ have followed the anchor by this point because authorization is done
        //   based on the target store, not the anchor.
        // Also note that this is consistent with SP's implementation even though this means that
        //   user A can share user B's anchor to shared folder C if A is an owner of C.
        throwIfNotAuthorized(token, Scope.WRITE_ACL, restObject.getSID());
        throwIfNotAuthorized(token, Scope.READ_FILES, restObject.getSID());

        // enforcing the same restriction as SP: an user _must_ be the owner of a store to
        // create links because we don't want org admins to be able to create links to
        // arbitrary content.
        User caller = _factUser.create(token.user());
        _factSF.create(restObject.getSID()).throwIfNotJoinedOwner(caller);

        String accessToken = createLinkSharingToken(caller, restObject,
                request.expires != null ? request.expires.getTime() : null);
        UrlShare urlShare = _factUrlShare.save(restObject, accessToken, caller.id());

        AuditableEvent auditEvent = createAuditEvent(token, ip, "link.create", urlShare)
                .add("soid", restObject.toStringFormal());

        // the end point will treat empty password as not setting password. Note that the backend
        // appear to support empty string as passwords but the web client does not.
        if (!isNullOrEmpty(request.password)) {
            urlShare.setPassword(request.password.getBytes(CHARSET), accessToken);
            auditEvent.add("set_password", true);
        }

        if (request.requireLogin != null) {
            urlShare.setRequireLogin(request.requireLogin, accessToken);
        }
        auditEvent.add("require_login", urlShare.getRequireLogin());

        if (request.expires != null) {
            urlShare.setExpires(request.expires.getTime(), accessToken);
            auditEvent.add("expiry", request.expires.getTime());
        }

        auditEvent.publish();

        String location = Service.DUMMY_LOCATION
                + 'v' + version
                + "/links/" + urlShare.getKey();

        return Response.created(new URI(location))
                .entity(toUrlShareResponse(urlShare))
                .build();
    }

    @Since("1.4")
    @DELETE
    @Path("/{key}")
    public Response removeURL(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        _zelda.deleteToken(urlShare.getToken());
        urlShare.delete();

        createAuditEvent(token, ip, "link.delete", urlShare).publish();

        return Response.noContent()
                .build();
    }

    @Since("1.4")
    @PUT
    @Path("/{key}")
    public Response updateURLInfo(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare,
            com.aerofs.rest.api.UrlShare request)
            throws ExBadArgs, ExNoPerm, ExNotFound,
            IOException, GeneralSecurityException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        boolean updatePassword = !isNullOrEmpty(request.password);
        boolean updateRequireLogin = request.requireLogin != null;
        boolean updateExpires = request.expires != null;

        checkArgument(updatePassword || updateRequireLogin || updateExpires);

        String newToken = createLinkSharingToken(urlShare);
        String oldToken = urlShare.getToken();

        AuditableEvent auditEvent = createAuditEvent(token, ip, "link.update", urlShare);

        if (updatePassword) {
            urlShare.setPassword(request.password.getBytes(CHARSET), newToken);
            auditEvent.add("set_password", true);
        }

        if (updateRequireLogin) {
            urlShare.setRequireLogin(request.requireLogin, newToken);
            auditEvent.add("require_login", request.requireLogin);
        }

        if (updateExpires) {
            urlShare.setExpires(request.expires.getTime(), newToken);
            auditEvent.add("expiry", request.expires.getTime());
        }

        _zelda.deleteToken(oldToken);

        auditEvent.publish();

        return Response.ok()
                .entity(toUrlShareResponse(urlShare))
                .build();
    }

    @Since("1.4")
    @PUT
    @Path("/{key}/password")
    public Response setURLPassword(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare,
            String password)
            throws ExBadArgs, ExNoPerm, ExNotFound,
            IOException, GeneralSecurityException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        checkArgument(!isNullOrEmpty(password));

        String newToken = createLinkSharingToken(urlShare);
        String oldToken = urlShare.getToken();

        urlShare.setPassword(password.getBytes(CHARSET), newToken);
        _zelda.deleteToken(oldToken);

        createAuditEvent(token, ip, "link.set_password", urlShare).publish();

        return Response.noContent().build();
    }

    @Since("1.4")
    @DELETE
    @Path("/{key}/password")
    public Response removeURLPassword(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        urlShare.removePassword();

        createAuditEvent(token, ip, "link.remove_password", urlShare).publish();

        return Response.noContent().build();
    }

    @Since("1.4")
    @PUT
    @Path("/{key}/expires")
    public Response setURLExpires(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare,
            Date expires)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        String newToken = createLinkSharingToken(urlShare);
        String oldToken = urlShare.getToken();

        urlShare.setExpires(expires.getTime(), newToken);
        _zelda.deleteToken(oldToken);

        createAuditEvent(token, ip, "link.set_expiry", urlShare)
                .add("expiry", expires.getTime())
                .publish();

        return Response.noContent().build();
    }

    @Since("1.4")
    @DELETE
    @Path("/{key}/expires")
    public Response removeURLExpires(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        // the old token is still valid in theory, but since we are generating a new token,
        // let's get rid of the old token for consistency.
        String newToken = createLinkSharingToken(urlShare);
        String oldToken = urlShare.getToken();

        urlShare.removeExpires(newToken);
        _zelda.deleteToken(oldToken);

        createAuditEvent(token, ip, "link.remove_expiry", urlShare).publish();

        return Response.noContent().build();
    }

    @Since("1.4")
    @PUT
    @Path("/{key}/require_login")
    public Response setURLRequireLogin(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        String newToken = createLinkSharingToken(urlShare);
        String oldToken = urlShare.getToken();

        urlShare.setRequireLogin(true, newToken);
        _zelda.deleteToken(oldToken);

        createAuditEvent(token, ip, "link.set_require_login", urlShare)
                .add("require_login", true)
                .publish();

        return Response.noContent().build();
    }

    @Since("1.4")
    @DELETE
    @Path("/{key}/require_login")
    public Response removeURLRequireLogin(@Auth IAuthToken token,
            @HeaderParam(X_REAL_IP) String ip,
            @PathParam("key") UrlShare urlShare)
            throws ExNoPerm, ExNotFound, IOException, SQLException
    {
        throwIfNotAuthorized(token, Scope.WRITE_ACL, urlShare);

        urlShare.setRequireLogin(false, urlShare.getToken());

        createAuditEvent(token, ip, "link.set_require_login", urlShare)
                .add("require_login", false)
                .publish();

        return Response.noContent().build();
    }

    private void throwIfNotAuthorized(IAuthToken token, Scope scope, UrlShare urlShare)
            throws ExNotFound, ExNoPerm, SQLException
    {
        throwIfNotAuthorized(token, scope, urlShare.getSid());
    }

    private void throwIfNotAuthorized(IAuthToken token, Scope scope, SID sid)
            throws ExNotFound, ExNoPerm, SQLException
    {
        requirePermissionOnFolder(scope, token, sid);
        if (token instanceof IUserAuthToken) {
            User caller = _factUser.create(((IUserAuthToken)token).user());
            SharedFolder sf = _factSF.create(sid);

            if (sf.getPermissionsNullable(caller) == null) { throw new ExNotFound(); }
            sf.throwIfNoPrivilegeToChangeACL(caller);
        }
    }

    private com.aerofs.rest.api.UrlShare toUrlShareResponse(UrlShare urlShare)
            throws ExNotFound, SQLException
    {
        Long expires = urlShare.getExpiresNullable();

        return new com.aerofs.rest.api.UrlShare(
                urlShare.getKey(),
                urlShare.getRestObject().toStringFormal(),
                urlShare.getToken(),
                urlShare.getCreatedBy().getString(),
                urlShare.getRequireLogin(),
                urlShare.hasPassword(),
                // never reveal the link password
                null,
                expires != null ? new Date(expires) : null);
    }

    private AuditableEvent createAuditEvent(IAuthToken token, @Nullable String ip,
            String event, UrlShare urlShare)
    {
        Object caller = token instanceof IUserAuthToken
                ? AuditCaller.fromUserAuthToken((IUserAuthToken)token)
                : "AeroFS Service";
        ip = isNullOrEmpty(ip) ? "Unknown" : ip;

        return _auditClient.event(AuditTopic.LINK, event)
                .add("ip", ip)
                .add("timestamp", _timeSource.getTime())
                .embed("caller", caller)
                .add("key", urlShare.getKey());
    }

    private String createLinkSharingToken(UrlShare urlShare)
            throws ExNotFound, IOException, SQLException
    {
        return createLinkSharingToken(_factUser.create(urlShare.getCreatedBy()),
                urlShare.getRestObject(),
                urlShare.getExpiresNullable());
    }

    private String createLinkSharingToken(User user, RestObject restObject, @Nullable Long expires)
            throws ExNotFound, IOException, SQLException
    {
        return _zelda.createAccessToken(restObject.toStringFormal(),
                _accessCodeProvider.createAccessCodeForUser(user),
                firstNonNull(expires, 0L));
    }
}
