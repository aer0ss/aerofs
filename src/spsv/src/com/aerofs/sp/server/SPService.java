package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
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
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExInviteeListEmpty;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OrganizationID;
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
import com.aerofs.proto.Sp.GetStripeDataReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.GetUnsubscribeEmailReply;
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
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.Builder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.PBStripeData;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.proto.Sp.RecertifyDeviceReply;
import com.aerofs.proto.Sp.RegisterDeviceReply;
import com.aerofs.proto.Sp.RemoveUserFromOrganizationReply;
import com.aerofs.proto.Sp.ResolveSignUpCodeReply;
import com.aerofs.proto.Sp.SignUpWithCodeReply;
import com.aerofs.proto.SpNotifications.PBACLNotification;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueElement;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueSize;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.SuccessError;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.audit.client.AuditClient.AuditTopic;
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
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sp.server.sharing_rules.ISharingRules;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.sv.common.EmailCategory;
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
import com.unboundid.ldap.sdk.LDAPSearchException;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.aerofs.base.BaseParam.VerkehrTopics.ACL_CHANNEL_TOPIC_PREFIX;
import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
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

    private VerkehrPublisher _verkehrPublisher;
    private VerkehrAdmin _verkehrAdmin;
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
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final Device.Factory _factDevice;
    private final SharedFolder.Factory _factSharedFolder;

    private final DeviceRegistrationEmailer _deviceRegistrationEmailer;
    private final RequestToSignUpEmailer _requestToSignUpEmailer;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final InvitationEmailer.Factory _factInvitationEmailer;

    private final JedisEpochCommandQueue _commandQueue;
    private final JedisThreadLocalTransaction _jedisTrans;
    private final Analytics _analytics;

    private final IdentitySessionManager _identitySessionManager;
    private Authenticator _authenticator;

    // Remember to udpate text in team_members.mako, team_settings.mako, and pricing.mako when
    // changing this number.
    private int _maxFreeMembersPerOrg = 3;

    // Whether to enforce payment checks
    private final Boolean ENABLE_PAYMENT =
            getBooleanProperty("sp.payment.enabled", true);

    // Whether to allow self sign-ups via RequestToSignUp()
    private final Boolean OPEN_SIGNUP =
            getBooleanProperty("open_signup", true);

    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public SPService(SPDatabase db, SQLThreadLocalTransaction sqlTrans,
            JedisThreadLocalTransaction jedisTrans, ISessionUser sessionUser,
            PasswordManagement passwordManagement,
            CertificateAuthenticator certificateAuthenticator, User.Factory factUser,
            Organization.Factory factOrg, OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice, CertificateDatabase certdb, EmailSubscriptionDatabase esdb,
            Factory factSharedFolder, InvitationEmailer.Factory factInvitationEmailer,
            DeviceRegistrationEmailer deviceRegistrationEmailer,
            RequestToSignUpEmailer requestToSignUpEmailer, JedisEpochCommandQueue commandQueue,
            Analytics analytics, IdentitySessionManager identitySessionManager,
            Authenticator authenticator, SharingRulesFactory sharingRules,
            SharedFolderNotificationEmailer sfnEmailer)
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
        _factUser = factUser;
        _factOrg = factOrg;
        _factOrgInvite = factOrgInvite;
        _factDevice = factDevice;
        _esdb = esdb;
        _factSharedFolder = factSharedFolder;

        _deviceRegistrationEmailer = deviceRegistrationEmailer;
        _requestToSignUpEmailer = requestToSignUpEmailer;
        _sfnEmailer = sfnEmailer;
        _factInvitationEmailer = factInvitationEmailer;

        _identitySessionManager = identitySessionManager;
        _sharingRules = sharingRules;

        _authenticator = authenticator;

        _commandQueue = commandQueue;
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

    public void setVerkehrClients_(VerkehrPublisher verkehrPublisher, VerkehrAdmin verkehrAdmin)
    {
        assert verkehrPublisher != null;
        assert verkehrAdmin != null;

        _verkehrPublisher = verkehrPublisher;
        _verkehrAdmin = verkehrAdmin;
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
            throw SystemUtil.fatalWithReturn(e2);
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

        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove the test (as userID will become required)
        User user = userID == null ?
                _sessionUser.getUser() :
                _factUser.createFromExternalID(userID);

        throwIfSessionUserIsNotOrAdminOf(user);

        if (firstName != null || lastName != null) {
            if (firstName == null || lastName == null) {
                throw new ExBadArgs("First and last name must both be non-null or both null");
            }

            FullName fullName = sanitizeName(firstName, lastName);
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
                    addToCommandQueueAndSendVerkehrMessage(peerDevice.id(),
                            CommandType.INVALIDATE_USER_NAME_CACHE);
                }
                if (deviceNameUpdated) {
                    l.info("cmd: inval device cache for " + peerDevice.id().toStringFormal());
                    addToCommandQueueAndSendVerkehrMessage(peerDevice.id(),
                            CommandType.INVALIDATE_DEVICE_NAME_CACHE);
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

        List<PBSharedFolder> pbs = sharedFolders2pb(org.listSharedFolders(maxResults, offset), org);

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
                sessionUser.getOrganization());

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
     */
    private List<PBSharedFolder> sharedFolders2pb(Collection<SharedFolder> sfs,
            Organization sessionOrg)
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
                    .setName(sf.getName())
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
            String osFamily, String osName, String deviceName)
            throws Exception
    {
        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove nulltoEmpty calls
        osFamily = Strings.nullToEmpty(osFamily);
        osName = Strings.nullToEmpty(osName);
        deviceName = Strings.nullToEmpty(deviceName);

        User user = _sessionUser.getUser();
        Device device = _factDevice.create(deviceId);

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

    @Override
    public ListenableFuture<RegisterDeviceReply> registerTeamServerDevice(ByteString deviceId,
            ByteString csr, String osFamily, String osName,
            String deviceName)
            throws Exception
    {
        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove nulltoEmpty calls
        osFamily = Strings.nullToEmpty(osFamily);
        osName = Strings.nullToEmpty(osName);
        deviceName = Strings.nullToEmpty(deviceName);

        User user = _sessionUser.getUser();

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
                UserID.fromExternal(userId).getString(), body, null, EmailCategory.SUPPORT);

        _sqlTrans.commit();

        return createVoidReply();
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
        // only allow empty invitee list when doing out-of-defaultRoot sharing
        if (srps.isEmpty() && !external) throw new ExInviteeListEmpty();

        _sqlTrans.begin();
        ISharingRules rules = _sharingRules.create(sharer);

        boolean created = saveSharedFolderIfNecessary(folderName, sf, sharer, external);
        ImmutableCollection<UserID> users = sf.getJoinedUserIDs();

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        for (SubjectPermissions srp : srps) {
            User sharee = _factUser.create(srp._subject);
            Permissions actualPermissions = rules.onUpdatingACL(sf, sharee, srp._permissions);
            emailers.add(createFolderInvitationAndEmailer(sf, sharer, sharee, actualPermissions, note,
                    folderName));
        }

        if (!suppressSharingRulesWarnings) rules.throwIfAnyWarningTriggered();

        // send verkehr notification as the last step of the transaction
        if (created || rules.shouldBumpEpoch()) publishACLs_(users);

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

    private InvitationEmailer createFolderInvitationAndEmailer(SharedFolder sf, User sharer,
            User sharee, Permissions permissions, String note, String folderName)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist,
            ExExternalServiceUnavailable, LDAPSearchException
    {
        SharedFolderState state = sf.getStateNullable(sharee);
        if (state == SharedFolderState.JOINED) {
            // TODO (WW) throw ExAlreadyJoined?
            throw new ExAlreadyExist(sharee.id() + " is already joined");
        } else if (state != null) {
            // Set user as pending if the user exists but in a non-joined state
            sf.setState(sharee, SharedFolderState.PENDING);
        } else {
            // Add a pending ACL entry if the user doesn't exist
            sf.addPendingUser(sharee, permissions, sharer);
        }

        InvitationEmailer emailer;
        if (sharee.exists()) {
            // send folder invitation email
            emailer = _factInvitationEmailer.createFolderInvitationEmailer(sharer, sharee,
                    folderName, note, sf.id(), permissions);
        } else {
            // send sign-up email
            emailer = inviteToSignUp(sharer, sharee, folderName, permissions, note)._emailer;
        }
        return emailer;
    }

    private static class InviteToSignUpResult
    {
        final InvitationEmailer _emailer;
        @Nullable final String _signUpCode;

        InviteToSignUpResult(InvitationEmailer emailer, @Nullable String signUpCode)
        {
            _emailer = emailer;
            _signUpCode = signUpCode;
        }
    }

    /**
     * Call this method to use an inviter name different from inviter.getFullName()._first
     */
    private InviteToSignUpResult inviteToSignUp(User inviter, User invitee, @Nullable String folderName,
            @Nullable Permissions permissions, @Nullable String note)
            throws SQLException, IOException, ExNotFound, ExExternalServiceUnavailable,
            LDAPSearchException
    {
        assert !invitee.exists();

        String code;
        if (_authenticator.isLocallyManaged(invitee.id())) {
            code = invitee.addSignUpCode();
            _esdb.insertEmailSubscription(invitee.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        } else {
            // No signup code is needed for auto-provisioned users.
            // They can simply sign in using their externally-managed account credentials.
            code = null;

            // We can't set up reminder emails as we do for locall-managed users, because
            // reminder email implementation requires valid signup codes. We can implement
            // different reminder emails if we'd like. In doing that, we need to remove the
            // reminder when creating the user during auto-provisioning.
        }

        InvitationEmailer emailer = _factInvitationEmailer.createSignUpInvitationEmailer(inviter,
                invitee, folderName, permissions, note, code);

        return new InviteToSignUpResult(emailer, code);
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
            addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.REFRESH_CRL);
        }

        // send notification email
        User sharer = sf.getSharerNullable(user);
        if (sharer != null && !sharer.id().isTeamServerID()) {
            _sfnEmailer.sendInvitationAcceptedNotificationEmail(sf, sharer, user);
        }

        // always call this method as the last step of the transaction
        publishACLs_(users);
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
            publishACLs_(users);
        }

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
                    .setFolderName(psf._sf.getName())
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
            InviteToSignUpResult res = inviteToSignUp(inviter, invitee, null, null, null);
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

        publishACLs_(users);

        _sqlTrans.commit();

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
                aclBuilder.setName(sf.getName());
                for (Entry<User, Permissions> en : sf.getJoinedUsersAndRoles().entrySet()) {
                    aclBuilder.addSubjectPermissions(PBSubjectPermissions.newBuilder()
                            .setSubject(en.getKey().id().getString())
                            .setPermissions(en.getValue().toPB()));
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

            // always call publishACLs_() as the last step of the transaction
            publishACLs_(affectedUsers);
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
        // always call this method as the last step of the transaction
        publishACLs_(sf.removeUser(subject));
        _sqlTrans.commit();

        return createVoidReply();
    }

    /**
     * Utility to minimize duped code in the below verkehr-related methods.
     * @param future either the verkehr publisher or admin.
     */
    private void verkehrFutureGet_(ListenableFuture<Void> future)
            throws Exception
    {
        try {
            future.get();
        } catch (InterruptedException e) {
            assert false : ("publisher client should never be interrupted");
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) {
                throw (Exception) e.getCause();
            } else {
                assert false : ("cannot handle arbitrary throwable");
            }
        }
    }

    /**
     * Publish ACL verkehr notificaitons for the specified users.
     *
     * NB. always call this method as the last step of the database transaction!
     */
    private void publishACLs_(Collection<UserID> users)
            throws Exception
    {
        for (Map.Entry<UserID, Long> entry : incrementACLEpochs_(users).entrySet()) {
            l.info(entry.getKey() + ": acl notification");

            PBACLNotification notification = PBACLNotification.newBuilder()
                    .setAclEpoch(entry.getValue())
                    .build();

            // Must match what is done on the client side.
            String aclTopic = ACL_CHANNEL_TOPIC_PREFIX + entry.getKey().getString();
            ListenableFuture<Void> published =
                    _verkehrPublisher.publish_(aclTopic, notification.toByteArray());

            verkehrFutureGet_(published);
        }
    }

    private Map<UserID, Long> incrementACLEpochs_(Collection<UserID> users) throws SQLException
    {
        Map<UserID, Long> m = Maps.newHashMap();
        for (UserID u : users) {
            m.put(u, _factUser.create(u).incrementACLEpoch());
        }
        return m;
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

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resetPassword(String password_reset_token,
            ByteString new_credentials)
        throws Exception
    {
        _sqlTrans.begin();
        _passwordManagement.resetPassword(password_reset_token, new_credentials);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> changePassword(ByteString old_credentials,
            ByteString new_credentials)
            throws Exception
    {
        _sqlTrans.begin();
        _passwordManagement.changePassword(_sessionUser.getUser().id(), old_credentials,
                new_credentials);
        _sqlTrans.commit();

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
        publishACLs_(users);

        _sqlTrans.commit();

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

        user.save(shaedSP, sanitizeName(firstName, lastName));

        // Unsubscribe user from the aerofs invitation reminder mailing list
        _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        _analytics.track(new SignUpEvent(user.id()));

        // N.B. do not remove the sign up invitation code so users can retry signing up using the
        // same link in the signup verification email. See this method's caller for detail.

        // Accept organization invitation if there is one associated with the signup code.
        OrganizationInvitation oi = _factOrgInvite.getBySignUpCodeNullable(signUpCode);
        if (oi == null) {
            return new SignUpWithCodeImplResult(Collections.<UserID>emptyList(), false);

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
        _authenticator.authenticateUser(user, credentials.toByteArray(), _sqlTrans,
                Authenticator.CredentialFormat.LEGACY);

        // Set the session cookie.
        _sessionUser.setUser(user);
        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

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
     * Check the given username and credentials. Throw an exception if
     * @param userId user identifier to check
     * @param cred credentials in cleartext
     * @return User object if authentication succeeds
     * @throws Exception if authentication fails
     */
    private User authByCredentials(String userId, ByteString cred) throws Exception
    {
        User user = _factUser.createFromExternalID(userId);
        _authenticator.authenticateUser(
                user, cred.toByteArray(), _sqlTrans, Authenticator.CredentialFormat.TEXT);

        l.info("SI: cred auth ok {}", user.id().getString());
        _auditClient.event(AuditTopic.USER, "signin")
                .add("user", user)
                .publishBlocking();

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
        }
        _sqlTrans.commit();
        l.info("SI (OpenID): " + user.toString());

        // Set the session cookie.
        _sessionUser.setUser(user);

        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

        return createReply(OpenIdSessionAttributes.newBuilder()
                .setUserId(attrs.getEmail())
                .setFirstName(attrs.getFirstName())
                .setLastName(attrs.getLastName())
                .build());
    }

    // TODO (WW) similar to UserID, create FullName.fromExternal(), fromInternal()?
    private FullName sanitizeName(String firstName, String lastName)
            throws ExBadArgs
    {
        firstName = firstName.trim();
        lastName = lastName.trim();
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new ExBadArgs("First and last names must not be empty");
        }
        return new FullName(firstName, lastName);
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
        if (!serials.isEmpty()) updateVerkehrCRL(serials);

        notifyPeerDevicesOfUnlink(owner, !serials.isEmpty());
        sendUnlinkCommand(did, erase);
    }

    /**
     * Notify the appropriate subset of peer devices when a device is unlinked.
     * Should be called with a SQL transaction.
     * @param owner user owning the unlinked device(s)
     */
    private void notifyPeerDevicesOfUnlink(User owner, boolean crlUpdated) throws Exception
    {
        Collection<Device> peerDevices = owner.getPeerDevices();

        // Tell peer devices to clean their sss database and refresh their certificate revocation
        // list.
        for (Device peer : peerDevices) {
            // Only need to refresh the CRL if we actually deleted a cert.
            if (crlUpdated) {
                addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.REFRESH_CRL);
            }

            // Clean the sync status database regardless, since we deleted a device.
            addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.CLEAN_SSS_DATABASE);
        }
    }

    private void sendUnlinkCommand(DID did, boolean erase) throws Exception
    {
        // Tell the actual device to perform the required local actions. Remember to flush the queue
        // first so that this is the only command left in the queue. The ensures that if the user
        // changes their password the unlink/wipe command can still be executed.
        if (erase) {
            addToCommandQueueAndSendVerkehrMessage(did,
                    CommandType.UNLINK_AND_WIPE_SELF, true);
        } else {
            addToCommandQueueAndSendVerkehrMessage(did, CommandType.UNLINK_SELF, true);
        }
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

        Command command = Command.newBuilder()
                .setEpoch(head.getEpoch())
                .setType(head.getType())
                .build();
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
        Command command = Command.newBuilder()
                .setEpoch(head.getEpoch())
                .setType(head.getType())
                .build();
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
                sessionUser.getOrganization());
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

        user.throwIfNotFound();

        if (!(caller.equals(user) || caller.isAdminOf(user))) throw new ExNoPerm("");

        // fetch organization before deactivation
        Organization org = user.getOrganization();

        // fetch device list before deactivation
        Collection<Device> devices = user.getDevices();

        ImmutableSet.Builder<Long> bd = ImmutableSet.builder();
        Collection<UserID> affectedUsers = user.deactivate(bd, caller.equals(user) ? null : caller);
        ImmutableCollection<Long> revokedSerials = bd.build();

        // IMPORTANT: no DB writes beyond this point

        // TODO: long term we need to ensure atomic sending of all vk messages in a transaction...
        for (Device device : devices) {
            sendUnlinkCommand(device.id(), eraseDevices);
        }

        updateVerkehrCRL(revokedSerials);

        notifyPeerDevicesOfUnlink(user, !revokedSerials.isEmpty());

        publishACLs_(affectedUsers);

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        _userTracker.signOutAll(user.id());

        return createReply(DeactivateUserReply.newBuilder().setStripeData(sd).build());
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

    private void addToCommandQueueAndSendVerkehrMessage(DID did, CommandType type)
        throws ExecutionException, InterruptedException
    {
        addToCommandQueueAndSendVerkehrMessage(did, type, false);
    }

    private void addToCommandQueueAndSendVerkehrMessage(DID did, CommandType type,
            boolean flushQueueFirst)
            throws ExecutionException, InterruptedException
    {
        _jedisTrans.begin();
        if (flushQueueFirst) {
            _commandQueue.delete(did);
        }
        Epoch epoch = _commandQueue.enqueue(did, type);
        _jedisTrans.commit();
        assert epoch != null;

        Command command = Command.newBuilder()
                .setEpoch(epoch.get())
                .setType(type)
                .build();
        _verkehrAdmin.deliverPayload_(did.toStringFormal(), command.toByteArray()).get();
    }

    private void updateVerkehrCRL(ImmutableCollection<Long> serials)
            throws Exception
    {
        l.info("command verkehr, #serials: " + serials.size());
        ListenableFuture<Void> succeeded = _verkehrAdmin.updateCRL(serials);

        verkehrFutureGet_(succeeded);
    }
}
