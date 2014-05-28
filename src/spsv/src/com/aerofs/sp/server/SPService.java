package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.acl.SubjectPermissionsList;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SignUpEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExInvalidEmailAddress;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesError;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBFolderInvitation;
import com.aerofs.proto.Common.PBPermissions;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.AcceptOrganizationInvitationReply;
import com.aerofs.proto.Sp.AckCommandQueueHeadReply;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.proto.Sp.CheckQuotaReply;
import com.aerofs.proto.Sp.CheckQuotaReply.PBStoreShouldCollect;
import com.aerofs.proto.Sp.CreateUrlReply;
import com.aerofs.proto.Sp.DeactivateUserReply;
import com.aerofs.proto.Sp.DeleteOrganizationInvitationForUserReply;
import com.aerofs.proto.Sp.DeleteOrganizationInvitationReply;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Sp.GetCommandQueueHeadReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetOrgPreferencesReply;
import com.aerofs.proto.Sp.GetOrganizationIDReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import com.aerofs.proto.Sp.GetQuotaReply;
import com.aerofs.proto.Sp.GetStripeDataReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.GetUnsubscribeEmailReply;
import com.aerofs.proto.Sp.GetUrlInfoReply;
import com.aerofs.proto.Sp.GetUserCRLReply;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.proto.Sp.ISPService;
import com.aerofs.proto.Sp.InviteToOrganizationReply;
import com.aerofs.proto.Sp.ListOrganizationInvitedUsersReply;
import com.aerofs.proto.Sp.ListOrganizationMembersReply;
import com.aerofs.proto.Sp.ListOrganizationMembersReply.PBUserAndLevel;
import com.aerofs.proto.Sp.ListOrganizationSharedFoldersReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListSharedFoldersReply;
import com.aerofs.proto.Sp.ListUserDevicesReply;
import com.aerofs.proto.Sp.ListUserDevicesReply.PBDevice;
import com.aerofs.proto.Sp.ListWhitelistedUsersReply;
import com.aerofs.proto.Sp.MobileAccessCode;
import com.aerofs.proto.Sp.OpenIdSessionAttributes;
import com.aerofs.proto.Sp.OpenIdSessionNonces;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.Builder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.PBStripeData;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.proto.Sp.RecertifyDeviceReply;
import com.aerofs.proto.Sp.RegisterDeviceCall.Interface;
import com.aerofs.proto.Sp.RegisterDeviceReply;
import com.aerofs.proto.Sp.RemoveUserFromOrganizationReply;
import com.aerofs.proto.Sp.ResolveSignUpCodeReply;
import com.aerofs.proto.Sp.SignUpWithCodeReply;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueElement;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueSize;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.SuccessError;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.Authenticator.CredentialFormat;
import com.aerofs.sp.authentication.IAuthority;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.InvitationHelper.InviteToSignUpResult;
import com.aerofs.sp.server.URLSharing.UrlShare;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.audit.AuditFolder;
import com.aerofs.sp.server.authorization.DeviceAuthClient;
import com.aerofs.sp.server.authorization.DeviceAuthEndpoint;
import com.aerofs.sp.server.authorization.DeviceAuthParam;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolder.Factory;
import com.aerofs.sp.server.lib.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.HttpSessionRemoteAddress;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sp.server.sharing_rules.ISharingRules;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

public class SPService implements ISPService
{
    private static final Logger l = Loggers.getLogger(SPService.class);

    // TODO (WW) remove dependency to these database objects
    private final SPDatabase _db;
    private final CertificateDatabase _certdb;
    private final EmailSubscriptionDatabase _esdb;

    private final SQLThreadLocalTransaction _sqlTrans;
    private final SharingRulesFactory _sharingRules;

    private ACLNotificationPublisher _aclPublisher;
    private AuditClient _auditClient;

    private SPActiveUserSessionTracker _userTracker;
    private SPSessionInvalidator _sessionInvalidator;
    private SPSessionExtender _sessionExtender;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.getUser() returns the userId associated with the current HttpSession.
    // Note that the session is set externally in SPServlet.
    private final ISessionUser _sessionUser;

    private final PasswordManagement _passwordManagement;
    private final CertificateAuthenticator _certauth;
    private final HttpSessionRemoteAddress _remoteAddress;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final Device.Factory _factDevice;
    private final SharedFolder.Factory _factSharedFolder;
    private final UrlShare.Factory _factUrlShare;

    private final DeviceRegistrationEmailer _deviceRegistrationEmailer;
    private final RequestToSignUpEmailer _requestToSignUpEmailer;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final InvitationEmailer.Factory _factInvitationEmailer;
    private final AsyncEmailSender _emailSender;

    private final JedisEpochCommandQueue _commandQueue;
    private final JedisThreadLocalTransaction _jedisTrans;
    private final CommandDispatcher _commandDispatcher;
    private final Analytics _analytics;

    private final IdentitySessionManager _identitySessionManager;
    private Authenticator _authenticator;

    private final InvitationHelper _invitationHelper;

    // Remember to udpate text in team_members.mako, team_settings.mako, and pricing.mako when
    // changing this number.
    private int _maxFreeMembersPerOrg = 3;

    // Whether to enforce payment checks
    private final Boolean ENABLE_PAYMENT =
            getBooleanProperty("sp.payment.enabled", true);

    // Whether to allow self sign-ups via RequestToSignUp()
    private final Boolean OPEN_SIGNUP =
            getBooleanProperty("open_signup", true);

    private final DeviceAuthClient _systemAuthClient =
            new DeviceAuthClient(new DeviceAuthEndpoint());

    public SPService(SPDatabase db,
            SQLThreadLocalTransaction sqlTrans,
            JedisThreadLocalTransaction jedisTrans,
            ISessionUser sessionUser,
            PasswordManagement passwordManagement,
            CertificateAuthenticator certificateAuthenticator,
            HttpSessionRemoteAddress remoteAddress,
            User.Factory factUser,
            Organization.Factory factOrg,
            OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice,
            CertificateDatabase certdb,
            EmailSubscriptionDatabase esdb,
            Factory factSharedFolder,
            InvitationEmailer.Factory factInvitationEmailer,
            DeviceRegistrationEmailer deviceRegistrationEmailer,
            RequestToSignUpEmailer requestToSignUpEmailer,
            JedisEpochCommandQueue commandQueue,
            Analytics analytics,
            IdentitySessionManager identitySessionManager,
            Authenticator authenticator,
            SharingRulesFactory sharingRules,
            SharedFolderNotificationEmailer sfnEmailer,
            AsyncEmailSender asyncEmailSender,
            UrlShare.Factory factUrlShare)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _certdb = certdb;

        _sqlTrans = sqlTrans;
        _jedisTrans = jedisTrans;
        _sessionUser = sessionUser;
        _passwordManagement = passwordManagement;
        _certauth = certificateAuthenticator;
        _remoteAddress = remoteAddress;
        _factUser = factUser;
        _factOrg = factOrg;
        _factOrgInvite = factOrgInvite;
        _factDevice = factDevice;
        _esdb = esdb;
        _factSharedFolder = factSharedFolder;
        _factUrlShare = factUrlShare;

        _deviceRegistrationEmailer = deviceRegistrationEmailer;
        _requestToSignUpEmailer = requestToSignUpEmailer;
        _sfnEmailer = sfnEmailer;
        _factInvitationEmailer = factInvitationEmailer;
        _emailSender = asyncEmailSender;

        _identitySessionManager = identitySessionManager;
        _sharingRules = sharingRules;

        _authenticator = authenticator;

        _invitationHelper = new InvitationHelper(_authenticator, _factInvitationEmailer, _esdb);

        _commandQueue = commandQueue;
        _commandDispatcher = new CommandDispatcher(_commandQueue, _jedisTrans);
        _analytics = checkNotNull(analytics);
    }

    /**
     * For testing only. Don't use in production.
     * TODO (WW) use configuration service.
     */
    public void setMaxFreeMembers(int maxFreeMembersPerOrg)
    {
        _maxFreeMembersPerOrg = maxFreeMembersPerOrg;
    }

    /**
     * Set the verkehr clients and things that depend on them - the command dispatcher.
     */
    public void setNotificationClients(VerkehrPublisher verkehrPublisher, VerkehrAdmin verkehrAdmin)
    {
        assert verkehrPublisher != null;
        assert verkehrAdmin != null;

        _commandDispatcher.setAdminClient(verkehrAdmin);
        _aclPublisher = new ACLNotificationPublisher(_factUser, verkehrPublisher);
    }

    public void setAuditorClient_(AuditClient auditClient)
    {
        _auditClient = auditClient;
    }

    public void setUserTracker(SPActiveUserSessionTracker userTracker)
    {
        assert userTracker != null;
        _userTracker = userTracker;
    }

    public void setSessionInvalidator(SPSessionInvalidator sessionInvalidator)
    {
        assert sessionInvalidator != null;
        _sessionInvalidator = sessionInvalidator;
    }

    public void setSessionExtender(SPSessionExtender sessionExtender)
    {
        assert sessionExtender != null;
        _sessionExtender = sessionExtender;
    }

    @Override
    public PBException encodeError(Throwable e)
    {
        String user;
        try {
            user = _sessionUser.exists() ? _sessionUser.getUser().id().getString() : "user unknown";
        } catch (ExNotAuthenticated e2) {
            throw SystemUtil.fatal(e2);
        }

        l.warn(user + ": " + Util.e(e,
                ExNoPerm.class,
                ExBadCredential.class,
                ExBadArgs.class,
                ExAlreadyExist.class,
                ExSharingRulesError.class,
                ExSharingRulesWarning.class,
                ExCannotResetPassword.class,
                ExNotAuthenticated.class));

        // Notify SPTransaction that an exception occurred.
        _sqlTrans.handleException();

        // Don't include stack trace here to avoid expose SP internals to the client side.
        return Exceptions.toPB(e);
    }

    @Override
    public ListenableFuture<Void> extendSession()
            throws ExNoPerm
    {
        // If the user has not signed in throw ExNoPerm. Deters but does not prevent DoS attacks.
        if (!_sessionUser.exists()) {
            throw new ExNoPerm();
        }

        String sessionID = _sessionUser.getSessionID();
        l.info("Extend session: " + sessionID);

        _sessionExtender.extendSession(sessionID);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetUserPreferencesReply> getUserPreferences(@Nullable ByteString deviceId)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        FullName fn = user.getFullName();

        GetUserPreferencesReply.Builder reply = GetUserPreferencesReply.newBuilder()
                .setFirstName(fn._first)
                .setLastName(fn._last)
                .setSignupDate(user.getSignupDate());

        if (deviceId != null) {
            // Some early Alpha testers don't have their device information in the database.
            // An UPUT adds the devices back only if they relaunches.
            Device device = _factDevice.create(deviceId);
            reply.setDeviceName(device.exists() ? device.getName() : "");
        }

        _sqlTrans.commit();

        return createReply(reply.build());
    }

    @Override
    public ListenableFuture<Void> setUserPreferences(String userID, String firstName,
            String lastName, ByteString deviceId, String deviceName)
            throws Exception
    {
        boolean userNameUpdated = false;
        boolean deviceNameUpdated = false;

        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        if (firstName != null || lastName != null) {
            if (firstName == null || lastName == null) {
                throw new ExBadArgs("First and last name must both be non-null or both null");
            }

            FullName fullName = FullName.fromExternal(firstName, lastName);
            l.info("{} set full name: {}, session user {}", user, fullName.getString(),
                    _sessionUser.getUser());
            user.setName(fullName);
            userNameUpdated = true;
        }

        if (deviceId != null) {
            Device device = _factDevice.create(deviceId);
            throwIfNotOwner(user, device);

            // TODO (WW) print session user in log headers
            l.info("{} set device name: {}, session user {}", user, deviceName,
                    _sessionUser.getUser());
            device.setName(deviceName);
            deviceNameUpdated = true;
        }

        // Verkehr messages and command queue related stuff.
        if (userNameUpdated || deviceNameUpdated)
        {
            Collection<Device> peerDevices = user.getPeerDevices();

            for (Device peerDevice : peerDevices) {
                if (userNameUpdated) {
                    l.info("cmd: inval user cache for " + peerDevice.id().toStringFormal());
                    _commandDispatcher.enqueueCommand(peerDevice.id(), createCommandMessage(
                            CommandType.INVALIDATE_USER_NAME_CACHE));
                }
                if (deviceNameUpdated) {
                    l.info("cmd: inval device cache for " + peerDevice.id().toStringFormal());
                    _commandDispatcher.enqueueCommand(peerDevice.id(), createCommandMessage(
                            CommandType.INVALIDATE_DEVICE_NAME_CACHE));
                }
            }
        }

        // Wrap the jedis calls and verkehr pushes in the sql transaction so if any of the above
        // fail we can ask the user to perform the rename later.
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrganizationIDReply> getOrganizationID()
            throws Exception
    {
        _sqlTrans.begin();
        User user = _sessionUser.getUser();

        String orgID = user.getOrganization().id().toHexString();
        _sqlTrans.commit();

        GetOrganizationIDReply reply = GetOrganizationIDReply.newBuilder()
                                                             .setOrgId(orgID)
                                                             .build();

        return createReply(reply);

    }

    @Override
    public ListenableFuture<ListOrganizationMembersReply> listOrganizationMembers(
            Integer maxResults, Integer offset)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();

        ListOrganizationMembersReply reply = ListOrganizationMembersReply.newBuilder()
                .addAllUserAndLevel(users2pb(org.listUsers(maxResults, offset)))
                .setTotalCount(org.countUsers())
                .build();

        _sqlTrans.commit();

        return createReply(reply);
    }

    private static List<PBUserAndLevel> users2pb(Collection<User> users)
            throws SQLException, ExNotFound
    {
        List<PBUserAndLevel> pb = Lists.newArrayListWithCapacity(users.size());
        for (User user : users) {
            pb.add(PBUserAndLevel.newBuilder()
                    .setUser(user2pb(user))
                    .setLevel(user.getLevel().toPB())
                    .build());
        }
        return pb;
    }

    @Override
    public ListenableFuture<ListOrganizationSharedFoldersReply> listOrganizationSharedFolders(
            Integer maxResults, Integer offset)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();
        Organization org = user.getOrganization();

        int sharedFolderCount = org.countSharedFolders();

        List<PBSharedFolder> pbs = sharedFolders2pb(org.listSharedFolders(maxResults, offset), org, user);

        _sqlTrans.commit();

        return createReply(ListOrganizationSharedFoldersReply.newBuilder()
                .addAllSharedFolder(pbs)
                .setTotalCount(sharedFolderCount)
                .build());
    }

    @Override
    public ListenableFuture<ListOrganizationInvitedUsersReply> listOrganizationInvitedUsers()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();

        ListOrganizationInvitedUsersReply.Builder builder =
                ListOrganizationInvitedUsersReply.newBuilder();
        for (OrganizationInvitation oi : user.getOrganization().getOrganizationInvitations()) {
            User invitee = oi.getInvitee();
            // Return the user as a pending invitation only if the user is locally managed.
            //
            // See also team_members.mako:inviteUser()
            if (_authenticator.isLocallyManaged(invitee.id())) {
                builder.addUserId(invitee.id().getString());
            }
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<ListSharedFoldersReply> listUserSharedFolders(String userID)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        User sessionUser = _sessionUser.getUser();
        List<PBSharedFolder> pbs = sharedFolders2pb(user.getSharedFolders(),
                sessionUser.getOrganization(), user);

        _sqlTrans.commit();

        return createReply(ListSharedFoldersReply.newBuilder().addAllSharedFolder(pbs).build());
    }

    @Override
    public ListenableFuture<ListUserDevicesReply> listUserDevices(String userID)
            throws ExNotAuthenticated, ExNoPerm, SQLException, ExFormatError, ExNotFound,
            ExEmptyEmailAddress
    {
        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        ListUserDevicesReply.Builder builder = ListUserDevicesReply.newBuilder();
        for (Device device : user.getDevices()) {
            builder.addDevice(PBDevice.newBuilder()
                    .setDeviceId(device.id().toPB())
                    .setDeviceName(device.getName())
                    .setOsFamily(device.getOSFamily())
                    .setOsName(device.getOSName()));
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    /**
     * @throws ExNoPerm if the session user is not {@code user} nor the admin of the {@code user}'s
     * organization
     */
    private void throwIfSessionUserIsNotOrAdminOf(User user)
            throws ExNoPerm, SQLException, ExNotFound, ExNotAuthenticated
    {
        User currentUser = _sessionUser.getUser();

        if (user.equals(currentUser)) return;

        // if the current user is different from the specified user, the current user must be
        // an admin of the organization the specified user belongs to.
        currentUser.throwIfNotAdmin();

        // TODO (WW) use this string for all ExNoPerm's?
        String noPermMsg = "you don't have permission to perform this action";

        // Throw early if the specified user doesn't exist rather than relying on the following
        // below. This is to prevent attacker from testing user existance.
        if (!user.exists()) throw new ExNoPerm(noPermMsg);

        if (!user.belongsTo(currentUser.getOrganization())) {
            throw new ExNoPerm(noPermMsg);
        }
    }

    /**
     * populate the builder with shared folder information including members and pending members.
     *
     * @param sessionOrg the organization of the session user
     * @param user the user that will be used to resolve the shared folder names. Shared folders
     * can have different names for different users.
     */
    private List<PBSharedFolder> sharedFolders2pb(Collection<SharedFolder> sfs,
            Organization sessionOrg, User user)
            throws ExNotFound, SQLException
    {
        // A cache to avoid excessive database queries. This should be obsolete with memcached.
        Map<User, FullName> user2nameCache = Maps.newHashMap();

        List<PBSharedFolder> pbs = Lists.newArrayListWithCapacity(sfs.size());
        for (SharedFolder sf : sfs) {

            // skip root stores. N.B. Organization.listSharedFolders never return root stores
            if (sf.id().isUserRoot()) continue;

            PBSharedFolder.Builder builder = PBSharedFolder.newBuilder()
                    .setStoreId(sf.id().toPB())
                    .setName(sf.getName(user))
                    .setOwnedByTeam(false);

            // fill in folder members
            for (UserPermissionsAndState entry : sf.getAllUsersRolesAndStates()) {
                sharedFolderMember2pb(sessionOrg, user2nameCache, builder,
                        entry._user, entry._permissions, entry._state);
            }

            pbs.add(builder.build());
        }
        return pbs;
    }

    private void sharedFolderMember2pb(Organization sessionOrg, Map<User, FullName> user2nameCache,
            Builder builder, User user, Permissions permissions, SharedFolderState state)
            throws ExNotFound, SQLException
    {
        // don't add Team Server to the list.
        if (user.id().isTeamServerID()) return;

        // the folder is owned by the session organization if an owner of the folder belongs to
        // the org.
        if (permissions.covers(Permission.MANAGE) && user.exists() && user.belongsTo(sessionOrg)) {
            builder.setOwnedByTeam(true);
        }

        builder.addUserPermissionsAndState(PBUserPermissionsAndState.newBuilder()
                .setPermissions(permissions.toPB())
                .setState(state.toPB())
                .setUser(user2pb(user, getUserFullName(user, user2nameCache))));
    }

    /**
     * Caches and returns the user's fullname.
     *
     * @return the user's fullname, either from cache or from db, or Fullname("", "") if the
     *   user does not exist.
     */
    private @Nonnull FullName getUserFullName(User user, Map<User, FullName> user2nameCache)
            throws SQLException
    {
        FullName fullname = user2nameCache.get(user);

        if (fullname == null) {
            try {
                fullname = user.getFullName();
            } catch (ExNotFound e) {
                // the user doesn't exist, which is realistic when we try to resolve pending
                //   members of a shared folder
                fullname = new FullName("", "");
            }

            user2nameCache.put(user, fullname);
        }

        return fullname;
    }

    private static PBUser.Builder user2pb(User user)
            throws SQLException, ExNotFound
    {
        return user2pb(user, user.getFullName());
    }

    private static PBUser.Builder user2pb(User user, FullName fn)
    {
        return PBUser.newBuilder()
                .setUserEmail(user.id().getString())
                .setFirstName(fn._first)
                .setLastName(fn._last);
    }

    @Override
    public ListenableFuture<Void> setAuthorizationLevel(final String userIdString,
            final PBAuthorizationLevel authLevel)
            throws Exception
    {
        _sqlTrans.begin();

        User requester = _sessionUser.getUser();
        User subject = _factUser.createFromExternalID(userIdString);
        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        l.info("Set auth requester=" + requester.id() + " subject=" + subject.id() + " auth=" +
                newAuth);

        // Verify caller and subject's organization match
        if (!subject.exists() || !requester.belongsTo(subject.getOrganization())) {
            throw new ExNoPerm(subject.id().getString() + " is not a member of your organization. " +
                    "Please invite the user to your organization first.");
        }

        if (requester.id().equals(subject.id())) {
            throw new ExNoPerm("cannot change authorization for yourself");
        }

        if (!requester.getLevel().covers(AuthorizationLevel.ADMIN) ||
                // requester's level must cover the new level
                !requester.getLevel().covers(newAuth)) {
            throw new ExNoPerm("you have no permissions to change authorization");
        }

        subject.setLevel(newAuth);
        _sessionInvalidator.invalidate(subject.id());

        _sqlTrans.commit();

        _auditClient.event(AuditTopic.USER, "user.org.authorization")
                .add("admin_user", requester.id())
                .add("target_user", subject.id())
                .add("new_level", newAuth)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrganizationInvitationsReply> getOrganizationInvitations()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();

        List<OrganizationInvitation> invitations = user.getOrganizationInvitations();

        List<GetOrganizationInvitationsReply.OrganizationInvitation> invitationsWireable =
                Lists.newArrayList();

        for (OrganizationInvitation invite : invitations) {
            GetOrganizationInvitationsReply.OrganizationInvitation.Builder builder =
                    GetOrganizationInvitationsReply.OrganizationInvitation.newBuilder();

            builder.setInviter(invite.getInviter().id().getString());
            builder.setOrganizationName(invite.getOrganization().getName());
            builder.setOrganizationId(invite.getOrganization().id().getInt());

            invitationsWireable.add(builder.build());
        }

        GetOrganizationInvitationsReply reply = GetOrganizationInvitationsReply.newBuilder()
                .addAllOrganizationInvitations(invitationsWireable)
                .build();

        _sqlTrans.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<GetOrgPreferencesReply> getOrgPreferences()
        throws Exception
    {
        _sqlTrans.begin();

        final Organization org = _sessionUser.getUser().getOrganization();

        final GetOrgPreferencesReply.Builder replyBuilder = GetOrgPreferencesReply.newBuilder()
                .setOrganizationName(org.getName())
                .setOrganizationContactPhone(org.getContactPhone());

        _sqlTrans.commit();

        return createReply(replyBuilder.build());
    }

    @Override
    public ListenableFuture<Void> setOrgPreferences(@Nullable final String orgName,
            @Nullable final String contactPhone)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();

        final Organization organization = user.getOrganization();

        // only update non null inputs

        if (StringUtils.isNotBlank(orgName)) {
            organization.setName(orgName);
        }

        if (StringUtils.isNotBlank(contactPhone)) {
            organization.setContactPhone(contactPhone);
        }

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetUnsubscribeEmailReply> unsubscribeEmail(String unsubscribeToken)
            throws Exception
    {
        _sqlTrans.begin();
        String email = _esdb.getEmail(unsubscribeToken);
        _esdb.removeEmailSubscription(unsubscribeToken);
        _sqlTrans.commit();

        GetUnsubscribeEmailReply unsubscribeEmail = GetUnsubscribeEmailReply.newBuilder()
                .setEmailId(email)
                .build();

        return createReply(unsubscribeEmail);
    }

    @Override
    public ListenableFuture<GetAuthorizationLevelReply> getAuthorizationLevel()
            throws Exception
    {
        _sqlTrans.begin();
        AuthorizationLevel level = _sessionUser.getUser().getLevel();
        _sqlTrans.commit();

        return createReply(GetAuthorizationLevelReply.newBuilder().setLevel(level.toPB()).build());
    }

    @Override
    public ListenableFuture<GetTeamServerUserIDReply> getTeamServerUserID()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();

        if (!user.getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm();
        }

        User tsUser = user.getOrganization().getTeamServerUser();

        GetTeamServerUserIDReply reply = GetTeamServerUserIDReply.newBuilder()
                .setId(tsUser.id().getString())
                .build();

        _sqlTrans.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<RegisterDeviceReply> registerDevice(ByteString deviceId, ByteString csr,
            String osFamily, String osName, String deviceName, List<Interface> interfaces)
            throws Exception
    {
        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);

        throwIfNotAuthorizedToRegisterDevice(user.id(), osFamily, osName, deviceName,
                _remoteAddress.get(), interfaces);

        CertificationResult cert = device.certify(new PKCS10CertificationRequest(csr.toByteArray()),
                user);

        _sqlTrans.begin();

        RegisterDeviceReply reply = saveDeviceAndCertificate(device, user, osFamily, osName,
                deviceName, cert);

        // Grab these information before releasing the transaction.
        String firstName = user.getFullName()._first;

        _sqlTrans.commit();

        // Sending an email doesn't need to be a part of the transaction
        _deviceRegistrationEmailer.sendDeviceCertifiedEmail(user.id().getString(), firstName,
                osFamily, deviceName, device.id());

        // see also: registerTeamServerDevice for a similar-structured event
        _auditClient.event(AuditTopic.DEVICE, "device.certify")
                .add("user", user.id())
                .add("device_id", device.id().toStringFormal())
                .add("device_type", "Desktop Client")
                .add("os_family", osFamily)
                .add("os_name", osName)
                .add("device_name", deviceName)
                .publish();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> setDeviceOSFamilyAndName(ByteString deviceId,
            String osFamily, String osName)
            throws Exception
    {
        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);

        _sqlTrans.begin();

        if (!device.exists()) {
            // This is to fix problems with some early Alpha testers whose devices are not in
            // the database.
            device.save(user, osFamily, osName, "(no name)");
        } else {
            throwIfNotOwner(user, device);
            device.setOSFamilyAndName(osFamily, osName);
        }

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setStripeCustomerID(String stripeCustomerId)
            throws Exception
    {
        if (Strings.isNullOrEmpty(stripeCustomerId)) throw new ExBadArgs();

        _sqlTrans.begin();
        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();
        user.getOrganization().setStripeCustomerID(stripeCustomerId);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteStripeCustomerID()
            throws Exception
    {
        _sqlTrans.begin();
        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();
        user.getOrganization().deleteStripeCustomerID();
        _sqlTrans.commit();

        return createVoidReply();
    }

    private void throwIfNotOwner(User user, Device device)
            throws ExNotFound, SQLException, ExNoPerm
    {
        if (!device.getOwner().equals(user)) {
            throw new ExNoPerm("your are not the owner of the device");
        }
    }

    /**
     * Throw if system is not authorized to register a device, i.e. install the AeroFS desktop
     * client or Team Server.
     *
     * This function is related to the device authorization subsystem. For details please consult
     * the design document: docs/design/device_authorization.md
     *
     * @throws ExBadArgs when the interfaces parameter is invalid.
     * @throws ExNoPerm when the system is not authorized to register a device. This occurs when
     * both of the following are true: (1) the device authorization endpoint has been configured and
     * is enabled in the bunker configuration interface, and (2) when the device authorization
     * endpoint says the device is not authorized.
     * @throws ExNoResource when there is some communication failure with the device authorization
     * endpoint and the appliance.
     */
    private void throwIfNotAuthorizedToRegisterDevice(UserID userID, String osFamily, String osName,
            String deviceName, String remoteAddress, List<Interface> interfaces)
            throws ExBadArgs, ExNoPerm, ExNoResource
    {
        if (!DeviceAuthParam.DEVICE_AUTH_ENDPOINT_ENABLED) {
            return;
        }

        l.info("{}: check endpoint for device authorization", userID);

        // Null check for backward compatibility.
        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove null check on proto version bump.
        if (interfaces == null || interfaces.size() < 1) {
            l.warn("{}: register device call with no interfaces param.", userID);
            throw new ExBadArgs();
        }

        boolean isSystemAuthorized;
        try {
             isSystemAuthorized = _systemAuthClient.isSystemAuthorized(userID, osFamily, osName,
                     deviceName, remoteAddress, interfaces);
        } catch (IOException e) {
            l.error("{}: I/O error contacting device authorization endpoint: {}", userID, e);
            throw new ExNoResource();
        } catch (GeneralSecurityException e) {
            l.error("{}: general security exception: {}", userID, e);
            throw new ExNoResource();
        }

        if (!isSystemAuthorized) {
            l.warn("{}: device registration rejected by endpoint.", userID);
            throw new ExNoPerm();
        }
    }

    @Override
    public ListenableFuture<RegisterDeviceReply> registerTeamServerDevice(ByteString deviceId,
            ByteString csr, String osFamily, String osName, String deviceName,
            List<Interface> interfaces)
            throws Exception
    {
        User user = _sessionUser.getUser();

        throwIfNotAuthorizedToRegisterDevice(user.id(), osFamily, osName, deviceName,
                _remoteAddress.get(), interfaces);

        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();

        user.throwIfNotAdmin();
        User tsUser = user.getOrganization().getTeamServerUser();

        _sqlTrans.commit();

        // This should not be part of a transaction because it involves an RPC call
        // FIXME: (PH) This leaves us vulnerable to a situation where the users organization changes
        // between the first transaction and the second transaction. This would result in a no-sync
        // on the Team Server.
        Device device = _factDevice.create(deviceId);
        CertificationResult cert = device.certify(new PKCS10CertificationRequest(csr.toByteArray()),
                tsUser);

        _sqlTrans.begin();

        RegisterDeviceReply reply = saveDeviceAndCertificate(device, tsUser, osFamily, osName,
                deviceName, cert);

        // Grab these information before releasing the transaction.
        String firstName = user.getFullName()._first;

        _sqlTrans.commit();

        // Sending an email doesn't need to be a part of the transaction
        _deviceRegistrationEmailer.sendTeamServerDeviceCertifiedEmail(user.id().getString(),
                firstName, osFamily, deviceName, device.id());

        _auditClient.event(AuditTopic.DEVICE, "device.certify")
                .add("user", user.id())
                .add("device_id", device.id().toStringFormal())
                .add("device_type", "Team Server")
                .add("device_name", deviceName)
                .add("os_family", osFamily)
                .add("os_name", osName)
                .publish();

        return createReply(reply);
    }

    private RegisterDeviceReply saveDeviceAndCertificate(Device device, User user, String osFamily,
            String osName, String deviceName, CertificationResult cert)
            throws Exception
    {
        device.save(user, osFamily, osName, deviceName);
        device.addCertificate(cert);

        return RegisterDeviceReply.newBuilder()
                .setCert(cert.toString())
                .build();
    }

    /**
     * Send an email to the user. This happens to only be used by the linux updater on failure.
     * TODO (MP) rename this to something more appropriate.
     */
    @Override
    public ListenableFuture<Void> emailUser(String userId, String body)
            throws Exception
    {
        _sqlTrans.begin();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME,
                _sessionUser.getUser().id().getString(), null,
                UserID.fromExternal(userId).getString(), body, null);

        _sqlTrans.commit();

        return createVoidReply();
    }

    private AuditableEvent auditSharing(SharedFolder sf, User caller, String event)
            throws SQLException, ExNotFound
    {
        return _auditClient.event(AuditTopic.SHARING, event)
                .embed("folder", new AuditFolder(sf.id(), sf.getName(caller)))
                .embed("caller", new AuditCaller(caller.id()));
    }

    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<PBSubjectPermissions> subjectPermissionsList, @Nullable String note,
            @Nullable Boolean external, @Nullable Boolean suppressSharingRulesWarnings)
            throws Exception
    {
        external = firstNonNull(external, false);
        suppressSharingRulesWarnings = firstNonNull(suppressSharingRulesWarnings, false);

        SharedFolder sf = _factSharedFolder.create(shareId);
        if (sf.id().isUserRoot()) throw new ExBadArgs("Cannot share root");

        User sharer = _sessionUser.getUser();
        List<SubjectPermissions> srps = SubjectPermissionsList.listFromPB(subjectPermissionsList);

        l.info("{} shares {} [{}] with {}", sharer, sf, external, srps);

        _sqlTrans.begin();
        ISharingRules rules = _sharingRules.create(sharer);

        List<AuditableEvent> events = Lists.newArrayList();
        boolean created = saveSharedFolderIfNecessary(folderName, sf, sharer, external);
        if (created) events.add(auditSharing(sf, sharer, "folder.create"));

        ImmutableCollection<UserID> users = sf.getJoinedUserIDs();

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        for (SubjectPermissions srp : srps) {
            User sharee = _factUser.create(srp._subject);
            Permissions actualPermissions = rules.onUpdatingACL(sf, sharee, srp._permissions);
            emailers.add(
                    _invitationHelper.createFolderInvitationAndEmailer(sf, sharer, sharee,
                            actualPermissions, note, folderName));

            events.add(auditSharing(sf, sharer, "folder.invite")
                    .add("target", sharee.id())
                    .embed("role", actualPermissions.toArray()));
        }

        if (!suppressSharingRulesWarnings) rules.throwIfAnyWarningTriggered();

        // send verkehr notification as the last step of the transaction
        if (created || rules.shouldBumpEpoch()) _aclPublisher.publish_(users);
        
        for (AuditableEvent e : events) e.publish();

        _sqlTrans.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    /**
     * @return whether the shared folder was created
     */
    private boolean saveSharedFolderIfNecessary(String folderName, SharedFolder sf,
            User sharer, boolean external)
            throws ExNotFound, SQLException, ExNoPerm, IOException, ExAlreadyExist
    {
        if (sf.exists()) {
            sf.throwIfNoPrivilegeToChangeACL(sharer);
            return false;
        }

        sf.save(folderName, sharer);
        sf.setExternal(sharer, external);
        return true;
    }



    @Override
    public ListenableFuture<Void> joinSharedFolder(ByteString sid, @Nullable Boolean external)
            throws Exception
    {
        external = firstNonNull(external, false);

        User user = _sessionUser.getUser();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        _sqlTrans.begin();
        joinSharedFolderImpl(external, user, sf);

        Permissions perm = sf.getPermissionsNullable(user);
        auditSharing(sf, user, "folder.join")
                .add("join_as", external ? "external" : "internal")
                .add("target", user.id())
                .embed("role", perm == null ? "" : perm.toArray())
                .publish();
        _sqlTrans.commit();

        return createVoidReply();
    }

    private void joinSharedFolderImpl(Boolean external, User user, SharedFolder sf)
            throws Exception
    {
        l.info("{} joins {}", user, sf);

        SharedFolderState state = sf.getStateNullable(user);

        if (state == SharedFolderState.JOINED) {
            l.info("already joined. ignore");
            return;
        }

        // Note 1. it also throws if the folder doesn't exist
        // Note 2. we allow users who have left the folder to rejoin
        if (state == null) throw new ExNotFound("No such invitation");

        // reset pending bit to make user a member of the shared folder
        ImmutableCollection<UserID> users = sf.setState(user, SharedFolderState.JOINED);

        // set the external bit for consistent auto-join behavior across devices
        sf.setExternal(user, external);

        Collection<Device> peerDevices = user.getPeerDevices();
        // Refresh CRLs for peer devices once this user joins the shared folder (since the peer user
        // map may have changed).
        for (Device peer : peerDevices) {
            l.info("{} crl refresh", peer.id());
            _commandDispatcher.enqueueCommand(peer.id(), createCommandMessage(
                    CommandType.REFRESH_CRL));
        }

        User sharer = sf.getSharerNullable(user);
        if (sharer != null && !sharer.id().isTeamServerID()) {

            // Set the shared folder name for the sharee to be the same name as for the sharer.

            // Note that by setting the name here in joinSharedFolder this means that if A shares
            // a folder with B and A later renames that folder before B joins, B will receive an
            // invitation email with the old name but will get the folder with the new name.
            // While this may seem odd, this is actually desirable. It should also be fairly rare.
            sf.setName(user, sf.getName(sharer));

            // Send notification email
            _sfnEmailer.sendInvitationAcceptedNotificationEmail(sf, sharer, user);
        }

        // always call this method as the last step of the transaction
        _aclPublisher.publish_(users);
    }

    @Override
    public ListenableFuture<Void> ignoreSharedFolderInvitation(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " ignore " + sf);

        // Note that it also throws if the folder or the user doesn't exist
        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        // Ignore the invitation by deleting the user.
        sf.removeUser(user);

        auditSharing(sf, user, "folder.delete_invitation")
                .add("target", user.id())
                .publish();

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> leaveSharedFolder(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info("{} leaves {}", user, sf);

        if (sf.id().isUserRoot()) throw new ExBadArgs("Cannot leave root folder");

        SharedFolderState state = sf.getStateNullable(user);

        if (state == null) throw new ExNotFound("No such folder or you're not member of the folder");

        // silently ignore leave call from pending users as multiple device of the same user
        // may make the call depending on the relative speeds of deletion propagation vs ACL
        // propagation
        if (state != SharedFolderState.PENDING) {
            if (state != SharedFolderState.JOINED) {
                throw new ExNotFound("You are not a member of this shared folder");
            }

            // set state
            Collection<UserID> users = sf.setState(user, SharedFolderState.LEFT);

            // always call this method as the last step of the transaction
            _aclPublisher.publish_(users);
        }

        auditSharing(sf, user, "folder.leave")
                .add("target", user.id())
                .publish();

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setSharedFolderName(ByteString sid, String name)
            throws Exception
    {
        if (name.isEmpty()) throw new ExBadArgs("Folder name cannot be empty");

        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info("{} renames {} to {}", user, sf, name);

        sf.setName(user, name);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<CheckQuotaReply> checkQuota(List<PBStoreUsage> stores)
            throws Exception
    {
        CheckQuotaReply.Builder responseBuilder = CheckQuotaReply.newBuilder();

        // When the SPService object is called, the auditclient is null. Since there is no state
        // and no initialization, we create a CheckQuotaHelper for each call so that we can use
        // the current value of auditClient
        CheckQuotaHelper checkQuotaHelper = new CheckQuotaHelper(
                _factSharedFolder,
                _emailSender,
                _auditClient,
                _sqlTrans);

        _sqlTrans.begin();
        User requester = _sessionUser.getUser();
        requester.throwIfNotTeamServer();
        _sqlTrans.commit();

        Map<SID, Long> storeUsageMap = CheckQuotaHelper.mapFromPBStoreUsageList(stores);

        _sqlTrans.begin();
        final Long quotaPerUser = requester.getOrganization().getQuotaPerUser();
        Set<SharedFolder> storesThatShouldCollectContent =
                checkQuotaHelper.checkQuota(storeUsageMap, quotaPerUser);
        _sqlTrans.commit();

        for (PBStoreUsage storeUsage : stores) {
            SharedFolder store = _factSharedFolder.create(storeUsage.getSid());
            responseBuilder.addStore(PBStoreShouldCollect.newBuilder()
                    .setSid(store.id())
                    .setCollectContent(storesThatShouldCollectContent.contains(store))
                    .build());
        }
        return createReply(responseBuilder.build());
    }

    @Override
    public ListenableFuture<GetQuotaReply> getQuota()
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _sessionUser.getUser();
        requester.throwIfNotAdmin();
        @Nullable Long quota = requester.getOrganization().getQuotaPerUser();
        _sqlTrans.commit();

        return createReply(quota == null ? GetQuotaReply.getDefaultInstance() :
                GetQuotaReply.newBuilder().setQuota(quota).build());
    }

    @Override
    public ListenableFuture<CreateUrlReply> createUrl(String soid, String token)
            throws Exception
    {
        RestObject restObject = RestObject.fromString(soid);
        SharedFolder sf = _factSharedFolder.create(restObject.getSID());
        User requester = _sessionUser.getUser();

        _sqlTrans.begin();
        sf.throwIfNoPrivilegeToChangeACL(requester);
        UrlShare link = _factUrlShare.save(restObject, token, requester.id());
        _sqlTrans.commit();

        return createReply(CreateUrlReply.newBuilder()
                .setUrlInfo(PBRestObjectUrl.newBuilder()
                        .setKey(link.getKey())
                        .setSoid(restObject.toStringFormal())
                        .setToken(token)
                        .setCreatedBy(requester.id().getString()))
                .build());
    }

    @Override
    public ListenableFuture<GetUrlInfoReply> getUrlInfo(String key)
            throws Exception
    {
        _sqlTrans.begin();
        UrlShare link = _factUrlShare.create(key);
        RestObject object = link.getRestObject();
        String token = link.getToken();
        @Nullable Long expires = link.getExpiresNullable();
        UserID createdBy = link.getCreatedBy();
        _sqlTrans.commit();

        PBRestObjectUrl.Builder objectBuilder = PBRestObjectUrl.newBuilder()
                .setKey(key)
                .setSoid(object.toStringFormal())
                .setCreatedBy(createdBy.getString())
                .setToken(token);
        if (expires != null) objectBuilder.setExpires(expires);

        return createReply(GetUrlInfoReply.newBuilder()
                .setUrlInfo(objectBuilder)
                .build());
    }

    @Override
    public ListenableFuture<Void> setUrlExpires(String key, Long expires, String newToken)
            throws Exception
    {
        User requester = _sessionUser.getUser();

        _sqlTrans.begin();
        UrlShare link = _factUrlShare.create(key);
        SID sid = link.getSid();
        SharedFolder sf = _factSharedFolder.create(sid);
        sf.throwIfNoPrivilegeToChangeACL(requester);
        link.setExpires(expires, newToken);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUrlExpires(String key, String newToken)
            throws Exception
    {
        User requester = _sessionUser.getUser();

        _sqlTrans.begin();
        UrlShare link = _factUrlShare.create(key);
        SID sid = link.getSid();
        SharedFolder sf = _factSharedFolder.create(sid);
        sf.throwIfNoPrivilegeToChangeACL(requester);
        link.removeExpires(newToken);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUrl(String key)
            throws Exception
    {
        User requester = _sessionUser.getUser();

        _sqlTrans.begin();
        UrlShare link = _factUrlShare.create(key);
        SID sid = link.getSid();
        SharedFolder sf = _factSharedFolder.create(sid);
        sf.throwIfNoPrivilegeToChangeACL(requester);
        link.delete();
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setQuota(Long quota)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _sessionUser.getUser();
        requester.throwIfNotAdmin();
        requester.getOrganization().setQuotaPerUser(quota);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeQuota()
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _sessionUser.getUser();
        requester.throwIfNotAdmin();
        requester.getOrganization().setQuotaPerUser(null);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListPendingFolderInvitationsReply> listPendingFolderInvitations()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();

        Collection<PendingSharedFolder> psfs = user.getPendingSharedFolders();

        ListPendingFolderInvitationsReply.Builder builder =
                ListPendingFolderInvitationsReply.newBuilder();
        for (PendingSharedFolder psf : psfs) {
            builder.addInvitation(PBFolderInvitation.newBuilder()
                    .setShareId(psf._sf.id().toPB())
                    .setFolderName(psf._sf.getName(user))
                    .setSharer(psf._sharer.getString()));
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> sendEmailVerification() throws ExBadArgs
    {
        throw new ExBadArgs();
    }

    @Override
    public ListenableFuture<Void> verifyEmail(String verificationCode) throws ExBadArgs
    {
        throw new ExBadArgs();
    }

    @Override
    public ListenableFuture<ResolveSignUpCodeReply> resolveSignUpCode(String tsc)
            throws SQLException, ExNotFound
    {
        l.info("tsc: " + tsc);

        _sqlTrans.begin();
        UserID result = _db.getSignUpCode(tsc);
        _sqlTrans.commit();

        return createReply(
                ResolveSignUpCodeReply.newBuilder().setEmailAddress(result.getString()).build());
    }

    @Override
    public ListenableFuture<Void> requestToSignUp(String emailAddress)
            throws Exception
    {
        if (!Util.isValidEmailAddress(emailAddress)) throw new ExInvalidEmailAddress();

        User user = _factUser.createFromExternalID(emailAddress);

        _sqlTrans.begin();

        // If it's an invitation-only system, only allow the first user to self sign up
        // (via the setup UI).
        if (!OPEN_SIGNUP && _factUser.hasUsers()) {
            throw new ExNoPerm("invitation-only sign up");
        }
        if (!_authenticator.isLocallyManaged(user.id())) {
            throw new ExNoPerm("auto-provisioned users don't need to request for sign-up");
        }

        @Nullable String signUpCode = user.exists() ? null : user.addSignUpCode();

        _sqlTrans.commit();

        // Send the email out of the transaction
        if (signUpCode != null) {
            // Retrieve the email address from the user id in case the original address is not
            // normalized.
            _requestToSignUpEmailer.sendRequestToSignUpEmail(user.id().getString(), signUpCode);
        } else {
            // The user already exists. Don't return special messages (errrors, warnings, etc)
            // to avoid leaking email information to attackers.
            _requestToSignUpEmailer.sendAlreadySignedUpEmail(user.id().getString());
        }

        _auditClient.event(AuditTopic.USER, "user.account.request")
                .add("email", emailAddress)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<InviteToOrganizationReply> inviteToOrganization(String userIdString)
            throws Exception
    {
        _sqlTrans.begin();

        User inviter = _sessionUser.getUser();
        User invitee = _factUser.create(UserID.fromExternal(userIdString));
        Organization org = inviter.getOrganization();

        l.info("{} sends organization invite to {}", inviter, invitee);

        inviter.throwIfNotAdmin();

        InvitationEmailer emailer;
        if (!invitee.exists()) {
            // The user doesn't exist. Send him a sign-up invitation email only, and associate the
            // signup code with the organization invitation. See signUpWithCode() on how this association is
            // consumed.
            InviteToSignUpResult res = _invitationHelper.inviteToSignUp(inviter, invitee, null,
                    null, null);
            // ignore the emailer returned by inviteToOrganization(), so we only send one email
            // rather than two.
            inviteToOrganization(inviter, invitee, org, res._signUpCode);
            emailer = res._emailer;

        } else if (!invitee.belongsTo(org)) {
            // the invitee exists and doesn't belong to the org. Send an invite.
            emailer = inviteToOrganization(inviter, invitee, org, null);

        } else {
            throw new ExAlreadyExist(invitee + " is already a member of the organization");
        }

        PBStripeData sd = getStripeData(org);
        throwIfPaymentRequiredAndNoCustomerID(sd);

        boolean locallyManaged = _authenticator.isLocallyManaged(invitee.id());

        _sqlTrans.commit();

        // send the email after transaction since it may take some time and it's okay to fail
        emailer.send();

        return createReply(InviteToOrganizationReply.newBuilder()
                .setStripeData(sd)
                .setLocallyManaged(locallyManaged)
                .build());
    }

    /**
     * Note that the invitee may not exist when the method is called.
     *
     * @param signUpCode The signUp code to be associated with the organization invitation. If the user
     * signs up with the associated code, the system will automatically accept the organization invitation.
     * See signUpWithCode().
     */
    InvitationEmailer inviteToOrganization(User inviter, User invitee, Organization org,
            @Nullable String signUpCode)
            throws ExAlreadyExist, SQLException, ExNotFound, ExAlreadyInvited, IOException
    {
        OrganizationInvitation invite = _factOrgInvite.create(invitee, org);

        if (invite.exists()) throw new ExAlreadyInvited();

        _factOrgInvite.save(inviter, invitee, org, signUpCode);
        _auditClient.event(AuditTopic.USER, "user.org.invite")
                .add("inviter", inviter.id())
                .add("invitee", invitee.id())
                .add("organization", org.id())
                .publish();

        return _factInvitationEmailer.createOrganizationInvitationEmailer(inviter, invitee);
    }

    @Override
    public ListenableFuture<AcceptOrganizationInvitationReply> acceptOrganizationInvitation(Integer orgID)
            throws Exception
    {
        _sqlTrans.begin();

        User accepter = _sessionUser.getUser();

        Organization orgOld = accepter.getOrganization();
        Organization orgNew = _factOrg.create(orgID);

        l.info("{} accept org invite to {}", accepter, orgNew);

        // Check to see if the user was actually invited to this organization.
        if (!_factOrgInvite.create(accepter, orgNew).exists()) throw new ExNotFound();

        // This will delete the organization invite
        // TODO (WW) this comment points to poor naming (or cohesion) in setOrganization
        Collection<UserID> users = accepter.setOrganization(orgNew, AuthorizationLevel.USER);

        // retrieve the stripe data for the old organization _after_ the user moves out.
        PBStripeData sd = getStripeData(orgOld);

        _aclPublisher.publish_(users);

        _sqlTrans.commit();
        _auditClient.event(AuditTopic.USER, "user.org.accept")
                .add("user", accepter.id())
                .add("previous_org", orgOld.id())
                .add("new_org", orgNew.id())
                .publish();

        return createReply(AcceptOrganizationInvitationReply.newBuilder().setStripeData(sd).build());
    }

    @Override
    public ListenableFuture<DeleteOrganizationInvitationReply> deleteOrganizationInvitation(
            Integer orgID)
            throws SQLException, ExNotAuthenticated, ExNotFound
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        Organization org = _factOrg.create(orgID);
        l.info("Delete org invite by " + user);

        _factOrgInvite.create(user, org).delete();

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        return createReply(DeleteOrganizationInvitationReply.newBuilder().setStripeData(sd).build());
    }

    @Override
    public ListenableFuture<DeleteOrganizationInvitationForUserReply> deleteOrganizationInvitationForUser(
            String userID) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();

        User invitee = _factUser.createFromExternalID(userID);
        _factOrgInvite.create(invitee, org).delete();

        // Remove the user's sign-up code as well as reminder emails so she will no longer be sign
        // up to the system at all. See also sign_up_workflow.md. Strictly, this is necessary only
        // for locally managed users (see Authenticator.isLocallyManaged(), but doing so for
        // externally managed users doesn't have side effects (no sign-up codes are ever generated
        // for these users).
        //
        // Don't do it for public deployment; otherwise the sign-up codes create by the user herself
        // or admins of other organizations would be lost, too.
        //
        // See also docs/design/sign_up_workflow.md.
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) {
            invitee.deleteAllSignUpCodes();
            _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        }

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        return createReply(
                DeleteOrganizationInvitationForUserReply.newBuilder().setStripeData(sd).build());
    }

    @Override
    public ListenableFuture<RemoveUserFromOrganizationReply> removeUserFromOrganization(
            String userId) throws Exception
    {
        _sqlTrans.begin();

        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) {
            throw new ExNoPerm("Removing users isn't supported in private deployment");
        }

        User admin = _sessionUser.getUser();
        User user = _factUser.createFromExternalID(userId);

        if (admin.equals(user)) throw new ExNoPerm("You can't remove yourself from an organization");

        throwIfSessionUserIsNotOrAdminOf(user);

        Organization newOrg = _factOrg.save();
        user.setOrganization(newOrg, AuthorizationLevel.ADMIN);

        PBStripeData sd = getStripeData(admin.getOrganization());

        _auditClient.event(AuditTopic.USER, "user.org.remove")
                .add("admin_user", admin.id())
                .add("target_user", user.id())
                .add("organization", admin.getOrganization().id())
                .publish();

        _sqlTrans.commit();


        return createReply(RemoveUserFromOrganizationReply.newBuilder().setStripeData(sd).build());
    }

    @Override
    public ListenableFuture<RecertifyDeviceReply> recertifyDevice(ByteString deviceId,
            ByteString csr)
            throws Exception
    {
        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);

        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();
        if (!device.exists()) {
            throw new ExNotFound("No device " + device + " exists for " + user);
        }
        _sqlTrans.commit();

        // Perform RPC to CA to get the CSR signed.
        CertificationResult cert = device.certify(new PKCS10CertificationRequest(csr.toByteArray()),
                user);

        // Add cert to DB.
        _sqlTrans.begin();
        device.addCertificate(cert);
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.DEVICE, "device.recertify")
                .add("user", user.id())
                .add("device", device.id().toStringFormal())
                .add("device_type", "Desktop Client")
                .publish();

        return createReply(RecertifyDeviceReply.newBuilder()
                .setCert(cert.toString())
                .build());
    }

    @Override
    public ListenableFuture<RecertifyDeviceReply> recertifyTeamServerDevice(ByteString deviceId,
            ByteString csr)
            throws Exception
    {
        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);

        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();
        user.throwIfNotAdmin();
        User tsUser = user.getOrganization().getTeamServerUser();
        if (!device.exists()) {
            throw new ExNotFound("No device " + device + " exists for " + tsUser);
        }
        _sqlTrans.commit();

        // This should not be part of a transaction because it involves an RPC call
        // FIXME: (DF) This leaves us vulnerable to a situation where the users organization changes
        // between the first transaction and the second transaction. This would result in a no-sync
        // on the Team Server.  But most people probably won't migrate organizations while manually
        // renewing certs.
        CertificationResult cert = device.certify(new PKCS10CertificationRequest(csr.toByteArray()),
                tsUser);

        _sqlTrans.begin();

        device.addCertificate(cert);

        _sqlTrans.commit();

        _auditClient.event(AuditTopic.DEVICE, "user.device.recertify")
                .add("user", user.id())
                .add("device", device.id().toStringFormal())
                .add("device_type", "Team Server")
                .publish();

        return createReply(RecertifyDeviceReply.newBuilder()
                    .setCert(cert.toString())
                    .build());
    }

    /**
     * Return a StripeData object for the specified org based on its organization size
     */
    private PBStripeData getStripeData(Organization org)
            throws SQLException, ExNotFound
    {
        PBStripeData.Builder builder = PBStripeData.newBuilder()
                .setQuantity(org.countOrganizationInvitations() + org.countUsers());

        StripeCustomerID scid = org.getStripeCustomerIDNullable();
        if (scid != null) builder.setCustomerId(scid.getString());

        return builder.build();
    }

    /**
     * Throw if the PBStripeData object indicates a paid plan but doesn't have a Stripe customer ID.
     */
    private void throwIfPaymentRequiredAndNoCustomerID(PBStripeData sd)
            throws ExNoStripeCustomerID
    {
        if (!ENABLE_PAYMENT) return;

        if (sd.getQuantity() > _maxFreeMembersPerOrg && !sd.hasCustomerId()) {
            throw new ExNoStripeCustomerID();
        }
    }

    @Override
    public ListenableFuture<GetStripeDataReply> getStripeData()
            throws SQLException, ExNoPerm, ExNotFound, ExNotAuthenticated
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        user.throwIfNotAdmin();

        GetStripeDataReply.Builder builder = GetStripeDataReply.newBuilder()
                .setStripeData(getStripeData(user.getOrganization()));

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm, ExNotAuthenticated, ExNotFound
    {
        User user = _sessionUser.getUser();
        GetACLReply.Builder bd = GetACLReply.newBuilder();

        l.info("getACL for {}", user.id());

        _sqlTrans.begin();

        long serverEpoch = user.getACLEpoch();
        if (serverEpoch == epoch) {
            l.info("no updates - matching epoch: {}", epoch);
        } else {
            for (SharedFolder sf : user.getSharedFolders()) {
                l.debug("add store {}", sf.id());
                PBStoreACL.Builder aclBuilder = PBStoreACL.newBuilder();
                aclBuilder.setStoreId(sf.id().toPB());
                aclBuilder.setExternal(sf.isExternal(user));
                aclBuilder.setName(sf.getName(user));
                for (Entry<User, Permissions> en : sf.getJoinedUsersAndRoles().entrySet()) {
                    PBSubjectPermissions.Builder spbd = PBSubjectPermissions.newBuilder()
                            .setSubject(en.getKey().id().getString())
                            .setPermissions(en.getValue().toPB());
                    // TS needs to know the external bit for all members to know
                    // when to auto-create anchors in root stores
                    if (user.id().isTeamServerID()) spbd.setExternal(sf.isExternal(en.getKey()));
                    aclBuilder.addSubjectPermissions(spbd);
                }
                bd.addStoreAcl(aclBuilder);
            }
        }

        _sqlTrans.commit();

        bd.setEpoch(serverEpoch);
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<Void> updateACL(final ByteString storeId, String subjectString,
            PBPermissions permissions, @Nullable Boolean suppressWarnings)
            throws Exception
    {
        suppressWarnings = firstNonNull(suppressWarnings, false);

        User user = _sessionUser.getUser();
        User subject = _factUser.createFromExternalID(subjectString);
        Permissions role = Permissions.fromPB(permissions);
        SharedFolder sf = _factSharedFolder.create(storeId);

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        _sqlTrans.begin();

        Permissions oldPermissions = sf.getPermissionsNullable(subject);
        if (oldPermissions != role) {
            sf.throwIfNoPrivilegeToChangeACL(user);

            ISharingRules rules = _sharingRules.create(user);
            role = rules.onUpdatingACL(sf, user, role);

            ImmutableCollection<UserID> affectedUsers = sf.setPermissions(subject, role);
            if (!suppressWarnings) rules.throwIfAnyWarningTriggered();

            _sfnEmailer.sendRoleChangedNotificationEmail(sf, user, subject, oldPermissions, role);

            auditSharing(sf, user, "folder.permission.update")
                    .add("target", subject.id())
                    .embed("new_role", role.toArray())
                    .embed("old_role", oldPermissions != null ? oldPermissions.toArray() : "")
                    .publish();

            // always call publishACLs_() as the last step of the transaction
            _aclPublisher.publish_(affectedUsers);
        }

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId, String subjectString)
            throws Exception
    {
        User user = _sessionUser.getUser();
        SharedFolder sf = _factSharedFolder.create(storeId);

        User subject = _factUser.createFromExternalID(subjectString);


        _sqlTrans.begin();
        sf.throwIfNoPrivilegeToChangeACL(user);

        auditSharing(sf, user, "folder.permission.delete")
                .add("target", subject.id())
                .publish();

        // always call this method as the last step of the transaction
        _aclPublisher.publish_(sf.removeUser(subject));
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signOut()
            throws Exception
    {
        _sessionUser.remove();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String userIdString)
            throws Exception
    {
        _sqlTrans.begin();
        _passwordManagement.sendPasswordResetEmail(_factUser.createFromExternalID(userIdString));
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.USER, "user.password.reset.request")
                .add("user", userIdString)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resetPassword(String password_reset_token,
            ByteString new_credentials)
        throws Exception
    {
        _sqlTrans.begin();
        User user = _passwordManagement.resetPassword(
                password_reset_token, new_credentials.toByteArray());
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.USER, "user.password.reset")
                .add("user", user.id())
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> changePassword(ByteString old_credentials,
            ByteString new_credentials)
            throws Exception
    {
        _sqlTrans.begin();
        User user = _passwordManagement.replacePassword(
                _sessionUser.getUser().id(),
                old_credentials.toByteArray(),
                new_credentials.toByteArray());
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.USER, "user.password.change")
                .add("user", user.id())
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<SignUpWithCodeReply> signUpWithCode(String signUpCode,
            ByteString password, String firstName, String lastName)
            throws Exception
    {
        // is the user joining a new organization or an existing one?
        boolean joinExistingOrg;

        // FIXME(jP): WAAAAAH! I don't want to do hash-generation in a database transaction!
        // But how else will I get the user id given nothing but this lousy signup code?
        _sqlTrans.begin();

        UserID userID = _db.getSignUpCode(signUpCode);
        User user = _factUser.create(userID);
        Collection<UserID> users;
        byte[] shaedSP = LocalCredential.hashScrypted(
                LocalCredential.deriveKeyForUser(userID, password.toByteArray()));

        if (user.exists()) {
            if (!user.isCredentialCorrect(shaedSP)) {
                _auditClient.event(AuditTopic.USER, "user.password.error")
                        .add("user", user.id())
                        .publish();
                throw new ExBadCredential("Password doesn't match the existing account");
            }
            // If the user already exists and the password matches the existing password, we do an
            // no-op. This is needed for the user to retry signing up using the link in the signup
            // verification email.
            users = Collections.emptyList();
            joinExistingOrg = true;
        } else {
            SignUpWithCodeImplResult result = signUpWithCodeImpl(signUpCode, user, firstName,
                    lastName, shaedSP);
            users = result._users;
            joinExistingOrg = result._joinedExistingOrg;
        }

        OrganizationID orgID = user.getOrganization().id();
        // always call this method as the last step of the transaction
        _aclPublisher.publish_(users);

        _sqlTrans.commit();

        _auditClient.event(AuditTopic.USER, "user.org.signup")
                .add("user", user.id())
                .add("first_name", firstName)
                .add("last_name", lastName)
                .add("organization", orgID)
                .add("is_admin", !joinExistingOrg) // TODO: is this valid? right?
                .publish();

        return createReply(SignUpWithCodeReply.newBuilder()
                .setOrgId(orgID.toHexString())
                .setExistingTeam(joinExistingOrg)
                .build());
    }

    private class SignUpWithCodeImplResult
    {
        final Collection<UserID> _users;
        final boolean _joinedExistingOrg; // is the user joining a new organization or an existing one?

        private SignUpWithCodeImplResult(Collection<UserID> users, boolean joinedExistingOrg)
        {
            _users = users;
            _joinedExistingOrg = joinedExistingOrg;
        }
    }
    /**
     * @return a collection of users to be passed to publishACLs_()
     */
    private SignUpWithCodeImplResult signUpWithCodeImpl(String signUpCode, User user,
            String firstName, String lastName, byte[] shaedSP)
            throws Exception
    {
        l.info("sign up {} with code {}", user, signUpCode);

        user.save(shaedSP, FullName.fromExternal(firstName, lastName));

        // Unsubscribe user from the aerofs invitation reminder mailing list
        _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        _analytics.track(new SignUpEvent(user.id()));

        // N.B. do not remove the sign up invitation code so users can retry signing up using the
        // same link in the signup verification email. See this method's caller for detail.

        // Accept organization invitation if there is one associated with the signup code.
        OrganizationInvitation oi = _factOrgInvite.getBySignUpCodeNullable(signUpCode);
        if (oi == null) {
            // make sure to update TS ACL epoch!
            return new SignUpWithCodeImplResult(
                    ImmutableList.of(user.getOrganization().id().toTeamServerUserID()), false);

        } else if (!oi.getInvitee().equals(user)) {
            l.error("the org invite ({} => {} to {}) associated with the signup code {} " +
                    "doesn't match the signup user {}. how possible?", oi.getInviter(),
                    oi.getInvitee(), oi.getOrganization(), signUpCode, user);
            throw new ExNotFound();

        } else {
            l.info("{} automatically accepts organization invitation to {}", user, oi.getOrganization());

            Collection<UserID> users = user.setOrganization(oi.getOrganization(),
                    AuthorizationLevel.USER);
            // setOrganization() should delete the invitation
            assert !oi.exists();

            return new SignUpWithCodeImplResult(users, true);
        }
    }

    /**
     * Sign in a _user_ - which requires a username and sha'ed credential.
     *
     * A signed-in user can certify devices.
     * Does not require a mutually-auth'ed session (obviously)
     *
     * == No new client paths should use this. See credentialSignIn(). ==
     *
     * == Remove this; review after January 2014. ==
     *
     * @throws ExEmptyEmailAddress if the user id is empty
     * @throws com.aerofs.base.ex.ExLicenseLimit if a seat limit prevents this new user from signing in
     * @throws ExBadCredential if username/password combination is incorrect
     */
    @Override
    public ListenableFuture<Void> signInUser(String userId, ByteString credentials)
            throws Exception
    {
        User user = _factUser.createFromExternalID(userId);

        // FIXME: Legacy clients will submit scrypt'ed credential information if the
        // user is an external user (or if local_credential signin is used, or if
        // the mode is Hybrid Cloud).
        // Review after January 2014
        IAuthority authority = _authenticator.authenticateUser(
                user, credentials.toByteArray(), _sqlTrans, CredentialFormat.LEGACY);

        // Set the session cookie.
        _sessionUser.setUser(user);
        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", user.id())
                .add("authority", authority)
                .publish();
        return createVoidReply();
    }

    /**
     * Unified user sign-in method - regardless of authenticator being used, this takes
     * a cleartext credential. When needed (i.e., when the user has an AeroFS-managed
     * password), we will perform the needed key derivation on the server side.
     *
     * IOW, no mo' client SCrypt.
     *
     * == All new client signin paths should use this entry point ==
     *
     * signIn() and signInUser() are *deprecated* and are being kept for compatibility with
     * clients that refuse to upgrade.
     *
     * A signed-in user can certify devices.
     * Does not require a mutually-auth'ed session.
     *
     * @throws ExEmptyEmailAddress if the user id is empty
     * @throws ExBadCredential if username/credential combination is incorrect
     */
    @Override
    public ListenableFuture<Void> credentialSignIn(String userId, ByteString credentials)
            throws Exception
    {
        User user = authByCredentials(userId, credentials);
        _sessionUser.setUser(user);
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());
        return createVoidReply();
    }

    /**
     * Check the given username and credentials. Throw an exception if the user/credential
     * pair is not valid.
     * @param userId user identifier to check
     * @param cred credentials in cleartext
     * @return User object if authentication succeeds
     * @throws Exception if authentication fails
     */
    private User authByCredentials(String userId, ByteString cred) throws Exception
    {
        User user = _factUser.createFromExternalID(userId);
        IAuthority authority = _authenticator.authenticateUser(
                user, cred.toByteArray(), _sqlTrans, CredentialFormat.TEXT);

        l.info("SI: cred auth ok {}", user.id().getString());
        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", user.id())
                .add("authority", authority)
                .publish();

        return user;
    }

    /**
     * Sign in a _device_ - which requires a signed device cert.
     *
     * Requires a mutually-auth'ed session (obviously)
     */
    @Override
    public ListenableFuture<Void> signInDevice(String userId, ByteString did)
            throws Exception
    {
        User user = _factUser.createFromExternalID(userId);

        Device device;
        try {
            device = _factDevice.create(DID.fromExternal(did.toByteArray()));
        } catch (ExFormatError e) {
            l.error(user + ": did malformed");
            throw new ExBadCredential();
        }

        _sqlTrans.begin();

        user.throwIfBadCertificate(_certauth, device);
        _sessionUser.setUser(user);
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

        _sqlTrans.commit();

        // Do not remote this event! It's important!
        //
        // This audit event is temporarily being used by Bloomberg to identify a device's last
        // known IP address for the purpose of detecting file transfers across network boundaries.
        // We rely on the ACL Syncronizer to reconnect to SP and sign in using the device
        // certificate on Verkehr reconnect.
        _auditClient.event(AuditTopic.DEVICE, "device.signin")
                .add("user", user.id())
                .add("ip", _remoteAddress.get())
                .publish();

        return createVoidReply();
    }

    /**
     * Validate the given userid/credential pair without signing in the user.
     *
     * IMPORTANT: This method only accepts cleartext credentials. TLS is your friend!
     *
     * @throws ExBadCredential if the user can not be authenticated
     */
    @Override
    public ListenableFuture<Void> validateCredential(String userID, ByteString credentials)
            throws Exception
    {
        authByCredentials(userID, credentials);
        return createVoidReply();
    }

    /**
     * A device authorization is a bearer token that you will be able to use exactly once
     * to sign in exactly one device. Because it is not limited to a particular device id
     * (how could it be, we are using to initial-set-up a device) it is very very sensitive!
     *
     * If you let someone see an authorization nonce, and don't use it,
     * an attacker can sign in one device as if they were you.
     *
     * Authorization nonces auto-expire if not used in N seconds.
     */
    @Override
    public ListenableFuture<MobileAccessCode> getMobileAccessCode()
            throws Exception
    {
        // this function requires User-level authentication
        _sqlTrans.begin();
        boolean userExists = _sessionUser.exists() && _sessionUser.getUser().exists();
        _sqlTrans.commit();

        if (!userExists) throw new ExNoPerm("Attempt to create device auth for non-existent user");
        l.info("Gen mobile access code for {}", _sessionUser.getUser().id());

        // Important: recall that IdentitySessionManager speaks seconds, not milliseconds,
        // due to the underlying key-expiration technology.
        int timeoutSec = 180;

        _auditClient.event(AuditTopic.DEVICE, "device.mobile.code")
                .add("user", _sessionUser.getUser().id())
                .add("timeout", timeoutSec)
                .publish();

        return createReply(MobileAccessCode.newBuilder()
                .setAccessCode(_identitySessionManager.createDeviceAuthorizationNonce(
                        _sessionUser.getUser(), 180))
                .build());
    }

    /**
     * Authorize a device by providing a device authorization nonce previously generated by
     * a signed-in user. The nonce is auto-deleted on first use (it is also self-destructing
     * after a short time).
     *
     * If the nonce is invalid, or it refers to a non-existing user, this method will throw
     * an appropriate exception.
     *
     * @throws ExBadCredential the nonce refers to a non-existing user
     * @throws com.aerofs.base.ex.ExExternalAuthFailure the nonce does not exist (used or expired)
     */
    @Override
    public ListenableFuture<AuthorizeMobileDeviceReply> authorizeMobileDevice(
            String nonce, String deviceInfo)
            throws Exception
    {
        if (_sessionUser.exists()) throw new ExNoPerm("User/device session state conflict");

        User user = _factUser.create(_identitySessionManager.getAuthorizedDevice(nonce));

        // avoid craziness if the user existed when the nonce was generated, but since deleted
        _sqlTrans.begin();
        if (!user.exists())
        {
            // TODO: can't easily unit-test this case until we can delete users
            l.warn("Authorized device nonce {} has invalid user {}", nonce, user.id().getString());
            _auditClient.event(AuditTopic.USER, "device.mobile.error")
                    .add("user", user.id())
                    .publish();
            throw new ExBadCredential("Authorized user does not exist.");
        }

        ListenableFuture<AuthorizeMobileDeviceReply> reply = createReply(
                AuthorizeMobileDeviceReply.newBuilder()
                        .setUserId(user.id().getString())
                        .setOrgId(user.getOrganization().id().toString())
                        .setIsOrgAdmin(user.isAdmin())
                        .build());
        _sqlTrans.commit();

        l.info("SI: authorized device for {}", user.id().getString());

        _auditClient.event(AuditTopic.DEVICE, "device.certify")
                .add("user", user.id())
                .add("device_type", "Mobile App")
                .add("device_id", deviceInfo)
//                .add("os_family", "unknown") // FIXME: hardcode iOS? Get this from somewhere?
//                .add("os_name", "unknown")   // FIXME: hardcode iOS? Get this from somewhere?
                .publish();
        return reply;
    }

    @Override
    public ListenableFuture<OpenIdSessionNonces> openIdBeginTransaction()
    {
        String session = _identitySessionManager.createSession(OpenId.DELEGATE_TIMEOUT);
        String delegate = _identitySessionManager.createDelegate(session, OpenId.DELEGATE_TIMEOUT);

        l.info("Created delegate nonce {} for session nonce {}", delegate, session);

        return createReply(OpenIdSessionNonces.newBuilder()
                .setSessionNonce(session)
                .setDelegateNonce(delegate)
                .build());
    }

    /**
     * Check the status of a pending session authentication. If the session is authenticated,
     * return the user attributes and then delete the pending auth record to avoid a replay.
     *
     * @throws ExBadCredential indicates the session is unknown or already returned once.
     * @throws com.aerofs.base.ex.ExLicenseLimit indicates that the user did not exist and adding
     *                         the user would exceed the organization's seat limit.
     */
    @Override
    public ListenableFuture<OpenIdSessionAttributes> openIdGetSessionAttributes(String session)
            throws Exception
    {
        // 1. getSession() may throw ExBadCredential which means the client should give up
        IdentitySessionAttributes attrs = _identitySessionManager.getSession(session);

        // 2. if attrs is null, the session nonce is ok but we are still waiting for authentication.
        // return uninitialized SessionAttributes and let the client try again.
        if (attrs == null) {
            return createReply(OpenIdSessionAttributes.getDefaultInstance());
        }

        User user = _factUser.createFromExternalID(attrs.getEmail());

        // TODO (WW) shouldn't it use IAuthenticator?
        _sqlTrans.begin();
        if (!user.exists()) {
            user.save(new byte[0], new FullName(attrs.getFirstName(), attrs.getLastName()));
            // notify TS of user creation (for root store auto-join)
            _aclPublisher.publish_(user.getOrganization().id().toTeamServerUserID());
        }
        _sqlTrans.commit();
        l.info("SI (OpenID): " + user.toString());

        // Set the session cookie.
        _sessionUser.setUser(user);

        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", attrs.getEmail())
                .add("authority", "OpenId")
                .publish();

        return createReply(OpenIdSessionAttributes.newBuilder()
                .setUserId(attrs.getEmail())
                .setFirstName(attrs.getFirstName())
                .setLastName(attrs.getLastName())
                .build());
    }

    @Override
    public ListenableFuture<GetUserCRLReply> getUserCRL(final Long crlEpoch)
        throws Exception
    {
        throw new UnsupportedOperationException();

        // TODO (MP) finish this...

        /*GetUserCRLReply reply = GetUserCRLReply.newBuilder()
                .setCrlEpoch(0L)
                .build();
        return createReply(reply);*/
    }

    @Override
    public ListenableFuture<GetCRLReply> getCRL()
        throws Exception
    {
        ImmutableList<Long> crl;

        _sqlTrans.begin();
        crl = _certdb.getCRL();
        _sqlTrans.commit();

        GetCRLReply reply = GetCRLReply.newBuilder()
                .addAllSerial(crl)
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> unlinkDevice(final ByteString deviceId, Boolean erase)
        throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);
        throwIfSessionUserIsNotOrAdminOf(device.getOwner());

        // TODO (WW) print session user in log headers
        l.info("{} unlinks {}, erase {}, session user {}", user, device.id().toStringFormal(),
                erase, _sessionUser.getUser());

        unlinkDeviceImplementation(device, erase);

        _auditClient.event(AuditTopic.DEVICE, erase ? "device.erase" : "device.unlink")
                .add("admin_user", user.id())
                .add("device", device.id().toStringFormal())
                .add("owner", device.getOwner().id())
                .publish();
        _sqlTrans.commit();

        return createVoidReply();
    }

    /**
     * Perform certificate revocation and update the persistent command queue.
     * Should be called with a SQL transaction.
     * @param device the device to unlink.
     */
    private void unlinkDeviceImplementation(Device device, boolean erase)
            throws Exception
    {
        DID did = device.id();
        User owner = device.getOwner();

        ImmutableSet<Long> serials = device.delete();

        UserManagement.propagateDeviceUnlink(_commandDispatcher, owner.getPeerDevices(), serials);

        _commandDispatcher.replaceQueue(did, createCommandMessage(
                erase ? CommandType.UNLINK_AND_WIPE_SELF : CommandType.UNLINK_SELF));
    }

    @Override
    public ListenableFuture<GetCommandQueueHeadReply> getCommandQueueHead(ByteString deviceID)
            throws SQLException, ExNoPerm, ExNotFound
    {
        Device device = _factDevice.create(deviceID);

        l.info("cmd head: " + device.id().toStringFormal());

        // N.B. do not do any permission verification here. If the user has changed their password
        // and issued an unlink command we still want the client to execute that command. Commands
        // queued are not sensitive, as long as the ack fails.

        _jedisTrans.begin();
        QueueElement head = _commandQueue.head(device.id());
        QueueSize size = _commandQueue.size(device.id());
        _jedisTrans.commit();

        if (!head.exists()) {
            GetCommandQueueHeadReply reply = GetCommandQueueHeadReply.newBuilder()
                    .setQueueSize(0L)
                    .build();

            return createReply(reply);
        }

        Command command = CommandUtil.createCommandFromMessage(head.getCommandMessage(),
                head.getEpoch());
        GetCommandQueueHeadReply reply = GetCommandQueueHeadReply.newBuilder()
                .setCommand(command)
                .setQueueSize(size.getSize())
                .build();

        l.info("cmd head reply: " + device.id().toStringFormal() + " size=" + size.getSize());
        return createReply(reply);
    }

    @Override
    public ListenableFuture<AckCommandQueueHeadReply> ackCommandQueueHead(ByteString deviceID,
            Long epoch, Boolean error)
            throws Exception
    {
        Device device = _factDevice.create(deviceID);

        // N.B. verification here is very important.
        _sqlTrans.begin();
        device.throwIfNotFound();
        device.throwIfNotOwner(_sessionUser.getUser());
        _sqlTrans.commit();

        l.info("cmd ack: " + device.id().toStringFormal() + " epoch=" + epoch + " error=" + error);

        _jedisTrans.begin();

        SuccessError result;
        QueueSize size = _commandQueue.size(device.id());
        if (error) {
            result = _commandQueue.retryLater(device.id(), epoch);
        } else {
            result = _commandQueue.dequeue(device.id(), epoch);
        }
        QueueElement head = _commandQueue.head(device.id());
        _jedisTrans.commit();

        if (result.error()) {
            // This could happen if the same command was enqueued before we could finish acking
            // the first instance; therefore this condition is perfectly normal. However, if for
            // some reason the actual ack functionality is broken and queues are never emptying, we
            // want some sort of logging to indicate that.
            l.warn("cmd ack failure: " + device.id().toStringFormal() + " epoch=" + epoch);
        }

        if (!head.exists()) {
           AckCommandQueueHeadReply reply = AckCommandQueueHeadReply.newBuilder()
                   .setQueueSize(0L)
                   .build();

           return createReply(reply);
        }

        // Now return the new head.
        Command command = CommandUtil.createCommandFromMessage(head.getCommandMessage(),
                head.getEpoch());
        AckCommandQueueHeadReply reply = AckCommandQueueHeadReply.newBuilder()
                .setCommand(command)
                .setQueueSize(size.getSize())
                .build();

        l.info("cmd ack reply: " + device.id().toStringFormal() + " size=" + size.getSize());
        return createReply(reply);
    }

    private static final GetDeviceInfoReply.PBDeviceInfo EMPTY_DEVICE_INFO =
            GetDeviceInfoReply.PBDeviceInfo.newBuilder().build();

    /**
     * Given a list of device IDs, this call will return a list of device info objects of the same
     * length.
     *
     * If we cannot find a given DID, return an empty device info object. If the session user does
     * not share anything with the owner of the given DID, also return an empty device info object.
     */
    @Override
    public ListenableFuture<GetDeviceInfoReply> getDeviceInfo(List<ByteString> dids)
            throws Exception
    {
        _sqlTrans.begin();

        Set<UserID> sharedUsers = _db.getSharedUsersSet(_sessionUser.getUser().id());

        GetDeviceInfoReply.Builder builder = GetDeviceInfoReply.newBuilder();
        for (ByteString did : dids) {
            Device device = _factDevice.create(did);
            User owner = device.getOwner();

            // If there is a permission error or the device does not exist, simply provide an empty
            // device info object.
            if (device.exists() && sharedUsers.contains(owner.id())) {
                builder.addDeviceInfo(GetDeviceInfoReply.PBDeviceInfo.newBuilder()
                    .setDeviceName(device.getName())
                    .setOwner(PBUser.newBuilder()
                        .setUserEmail(owner.id().getString())
                        .setFirstName(owner.getFullName()._first)
                        .setLastName(owner.getFullName()._last)));
            } else {
                builder.addDeviceInfo(EMPTY_DEVICE_INFO);
            }
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<ListSharedFoldersReply> listSharedFolders(List<ByteString> sids)
            throws Exception
    {
        User sessionUser = _sessionUser.getUser();
        ImmutableList.Builder<SharedFolder> foldersBuilder = ImmutableList.builder();

        _sqlTrans.begin();
        for (ByteString sid : sids) {
            SharedFolder folder = _factSharedFolder.create(sid);
            if (folder.getPermissionsNullable(sessionUser) == null) throw new ExNoPerm();
            foldersBuilder.add(folder);
        }

        List<PBSharedFolder> pbFolders = sharedFolders2pb(foldersBuilder.build(),
                sessionUser.getOrganization(), sessionUser);
        _sqlTrans.commit();

        return createReply(ListSharedFoldersReply.newBuilder()
                .addAllSharedFolder(pbFolders)
                .build());
    }

    @Override
    public ListenableFuture<Void> addUserToWhitelist(final String userEmail)
            throws Exception
    {
        User caller = _sessionUser.getUser();
        User user = _factUser.createFromExternalID(userEmail);

        l.debug("{} add {} to whitelist", caller.id().getString(), user.id().getString());

        _sqlTrans.begin();

        caller.throwIfNotAdmin();  // throws ExNoPerm
        user.throwIfNotFound();  // throws ExNotFound

        user.setWhitelisted(true);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUserFromWhitelist(final String userEmail)
            throws Exception
    {
        User caller = _sessionUser.getUser();
        User user = _factUser.createFromExternalID(userEmail);

        l.debug("{} remove {} from whitelist", caller.id().getString(), user.id().getString());

        _sqlTrans.begin();

        caller.throwIfNotAdmin();  // throws ExNoPerm
        user.throwIfNotFound();  // throws ExNotFound

        user.setWhitelisted(false);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListWhitelistedUsersReply> listWhitelistedUsers()
            throws Exception
    {
        User caller = _sessionUser.getUser();

        l.debug("list whitelisted users: {}", caller.id().getString());

        ListWhitelistedUsersReply.Builder builder = ListWhitelistedUsersReply.newBuilder();

        _sqlTrans.begin();
        caller.throwIfNotAdmin();  // throws ExNoPerm
        for (User user : caller.getOrganization().listWhitelistedUsers()) {
            builder.addUser(user2pb(user)).build();
        }
        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<DeactivateUserReply> deactivateUser(String userId, Boolean eraseDevices)
            throws Exception
    {
        User caller = _sessionUser.getUser();
        User user = _factUser.createFromExternalID(userId);

        _sqlTrans.begin();
        Organization org = user.getOrganization();

        UserManagement.deactivateByAdmin(caller, user, eraseDevices, _commandDispatcher, _aclPublisher);

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        _userTracker.signOutAll(user.id());

        return createReply(DeactivateUserReply.newBuilder().setStripeData(sd).build());
    }

    /**
     * If this is in private deployment, we know the Private Organization is enough context to
     * get a seat count. If not in private deployment, return zero.
     */
    int getSeatCountForPrivateOrg() throws SQLException
    {
        int retval = 0;

        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) {
            _sqlTrans.begin();
            Organization privateOrg = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
            retval = privateOrg.countUsers();
            _sqlTrans.commit();
        }
        return retval;
    }

    /**
     * Convenience method to set the reply an exception into the reply
     */
    private static ListenableFuture<Void> createVoidReply()
    {
        return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
    }

    private static <T> ListenableFuture<T> createReply(T reply)
    {
        return UncancellableFuture.createSucceeded(reply);
    }

    static private void throwOnInvalidOffset(int offset) throws ExBadArgs
    {
        if (offset < 0) throw new ExBadArgs("offset is negative");
    }

    // To avoid DoS attacks, do not permit listUsers queries to exceed 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    static private void throwOnInvalidMaxResults(int maxResults) throws ExBadArgs
    {
        if (maxResults > ABSOLUTE_MAX_RESULTS) throw new ExBadArgs("maxResults is too big");
        else if (maxResults < 0) throw new ExBadArgs("maxResults is a negative number");
    }
}
