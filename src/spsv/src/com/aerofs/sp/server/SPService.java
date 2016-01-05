package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.acl.SubjectPermissionsList;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SignUpEvent;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.*;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExInvalidEmailAddress;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Common.*;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.*;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.proto.Sp.CheckQuotaReply.PBStoreShouldCollect;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.ListGroupStatusInSharedFolderReply.PBUserAndState;
import com.aerofs.proto.Sp.ListOrganizationMembersReply.PBUserAndLevel;
import com.aerofs.proto.Sp.ListUserDevicesReply.PBDevice;
import com.aerofs.proto.Sp.PBSharedFolder.Builder;
import com.aerofs.proto.Sp.PBSharedFolder.PBGroupPermissions;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.RegisterDeviceCall.Interface;
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
import com.aerofs.sp.authentication.LdapConfiguration;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.InvitationHelper.InviteToSignUpResult;
import com.aerofs.sp.server.LdapGroupSynchronizer.AffectedUsersAndError;
import com.aerofs.sp.server.audit.AuditCaller;
import com.aerofs.sp.server.audit.AuditFolder;
import com.aerofs.sp.server.authorization.DeviceAuthClient;
import com.aerofs.sp.server.authorization.DeviceAuthEndpoint;
import com.aerofs.sp.server.authorization.DeviceAuthParam;
import com.aerofs.sp.server.email.*;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.Group.AffectedUserIDsAndInvitedFolders;
import com.aerofs.sp.server.lib.group.Group.AffectedUserIDsAndInvitedUsers;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.Organization.TwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.ISession;
import com.aerofs.sp.server.lib.session.ISession.Provenance;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
import com.aerofs.sp.server.lib.session.RequestRemoteAddress;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolder.AffectedAndNeedsEmail;
import com.aerofs.sp.server.lib.sf.SharedFolder.Factory;
import com.aerofs.sp.server.lib.sf.SharedFolder.GroupPermissions;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.twofactor.RecoveryCode;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sp.server.settings.token.UserSettingsToken;
import com.aerofs.sp.server.sharing_rules.ISharingRules;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.collect.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.*;
import static com.aerofs.lib.Util.urlEncode;
import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class SPService implements ISPService
{
    private static final Logger l = Loggers.getLogger(SPService.class);

    // TODO (WW) remove dependency to these database objects
    private final SPDatabase _db;
    private final EmailSubscriptionDatabase _esdb;

    private final SQLThreadLocalTransaction _sqlTrans;
    private final SharingRulesFactory _sharingRules;

    private final ACLNotificationPublisher _aclPublisher;
    private final AuditClient _auditClient;

    private SPActiveUserSessionTracker _userTracker;
    private SPSessionInvalidator _sessionInvalidator;
    private SPSessionExtender _sessionExtender;

    // A proxy object to the current session.
    private final ISession _session;

    private final PasswordManagement _passwordManagement;
    private final CertificateAuthenticator _certauth;
    private final RequestRemoteAddress _remoteAddress;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final Device.Factory _factDevice;
    private final SharedFolder.Factory _factSharedFolder;
    private final UrlShare.Factory _factUrlShare;
    private final UserSettingsToken.Factory _factUserSettingsToken;
    private final Group.Factory _factGroup;

    private final DeviceRegistrationEmailer _deviceRegistrationEmailer;
    private final RequestToSignUpEmailer _requestToSignUpEmailer;
    private final TwoFactorEmailer _twoFactorEmailer;
    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final InvitationEmailer.Factory _factInvitationEmailer;
    private final AsyncEmailSender _emailSender;

    private final JedisEpochCommandQueue _commandQueue;
    private final JedisThreadLocalTransaction _jedisTrans;
    private final CommandDispatcher _commandDispatcher;
    private final Analytics _analytics;

    private final IdentitySessionManager _identitySessionManager;
    private final Authenticator _authenticator;
    private final LdapGroupSynchronizer _ldapGroupSynchronizer;

    private final InvitationHelper _invitationHelper;

    // Whether to allow self sign-ups via RequestToSignUp()
    private final Boolean OPEN_SIGNUP =
            getBooleanProperty("open_signup", false);

    // If true, server will start a periodic job to sync groups with LDAP endpoint representation
    public final boolean LDAP_GROUP_SYNCING_ENABLED =
            getBooleanProperty("ldap.groupsyncing.enabled", false) &&
            Identity.AUTHENTICATOR == Identity.Authenticator.EXTERNAL_CREDENTIAL;

    // The daily time at which to sync with LDAP, in UTC, in the form HH:MM
    public final String LDAP_GROUP_SYNCING_TIME =
            getStringProperty("ldap.groupsyncing.time", "00:00");

    private final DeviceAuthClient _systemAuthClient =
            new DeviceAuthClient(new DeviceAuthEndpoint());

    private final JedisRateLimiter _rateLimiter;

    private final Zelda _zelda;
    private final AccessCodeProvider _accessCodeProvider;

    @Inject
    public SPService(SPDatabase db,
            SQLThreadLocalTransaction sqlTrans,
            JedisThreadLocalTransaction jedisTrans,
            ISession session,
            PasswordManagement passwordManagement,
            CertificateAuthenticator certificateAuthenticator,
            RequestRemoteAddress remoteAddress,
            User.Factory factUser,
            Organization.Factory factOrg,
            OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice,
            EmailSubscriptionDatabase esdb,
            Factory factSharedFolder,
            InvitationEmailer.Factory factInvitationEmailer,
            DeviceRegistrationEmailer deviceRegistrationEmailer,
            RequestToSignUpEmailer requestToSignUpEmailer,
            TwoFactorEmailer twoFactorEmailer,
            JedisEpochCommandQueue commandQueue,
            Analytics analytics,
            IdentitySessionManager identitySessionManager,
            Authenticator authenticator,
            SharingRulesFactory sharingRules,
            SharedFolderNotificationEmailer sfnEmailer,
            AsyncEmailSender asyncEmailSender,
            UrlShare.Factory factUrlShare,
            UserSettingsToken.Factory factUserSettingsToken,
            Group.Factory factGroup,
            JedisRateLimiter rateLimiter,
            ScheduledExecutorService scheduledExecutor,
            SSMPConnection ssmp,
            AuditClient auditClient,
            ACLNotificationPublisher aclPublisher,
            Zelda zelda,
            AccessCodeProvider accessCodeProvider)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;

        _sqlTrans = sqlTrans;
        _jedisTrans = jedisTrans;
        _session = session;
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
        _factUserSettingsToken = factUserSettingsToken;
        _factGroup = factGroup;
        _deviceRegistrationEmailer = deviceRegistrationEmailer;
        _requestToSignUpEmailer = requestToSignUpEmailer;
        _twoFactorEmailer = twoFactorEmailer;
        _sfnEmailer = sfnEmailer;
        _factInvitationEmailer = factInvitationEmailer;
        _emailSender = asyncEmailSender;

        _identitySessionManager = identitySessionManager;
        _sharingRules = sharingRules;

        _authenticator = authenticator;
        _auditClient = auditClient;
        _aclPublisher = aclPublisher;

        _invitationHelper = new InvitationHelper(_authenticator, _factInvitationEmailer, _esdb);
        _ldapGroupSynchronizer = new LdapGroupSynchronizer(new LdapConfiguration(), factUser,
                factGroup, _invitationHelper);

        _commandQueue = commandQueue;
        _commandDispatcher = new CommandDispatcher(_commandQueue, _jedisTrans, ssmp);
        _analytics = checkNotNull(analytics);

        _rateLimiter = rateLimiter;

        _zelda = zelda;
        _accessCodeProvider = accessCodeProvider;

        startPeriodicSyncing(scheduledExecutor, () -> {
            try {
                syncGroupsWithLDAPImpl(null);
            } catch (Exception e) {
                //have to catch and suppress this exception because Runnable.run() can't throw
                //exceptions, this also allows us to continue periodically syncing even if the
                //previous attempt failed
                l.warn("failed to sync LDAP groups", e);
            }
        });
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

    private void startPeriodicSyncing(ScheduledExecutorService scheduler, Runnable sync)
    {
        if (LDAP_GROUP_SYNCING_ENABLED) {
            // first get the difference in seconds between now and the SYNCING_TIME set, then set it
            // to repeat once a day at that time
            Duration difference = Duration.between(LocalTime.now(Clock.systemUTC()),
                    LocalTime.parse(LDAP_GROUP_SYNCING_TIME));
            // if the time has already passed today, the difference will be negative - add 24 hours
            // so we get to the scheduled time tomorrow
            if (difference.isNegative()) {
                difference = difference.plusDays(1);
            }
            scheduler.scheduleAtFixedRate(sync, difference.getSeconds(), 60 * 60 * 24, TimeUnit.SECONDS);
        } else {
            scheduler.shutdown();
        }
    }

    @Override
    public PBException encodeError(Throwable e)
    {
        String user;
        user = _session.isAuthenticated() ? _session.getUserNullable().id().getString()
                : "user unknown";

        l.warn(user + ": " + Util.e(e,
                ExNoPerm.class,
                ExBadCredential.class,
                ExBadArgs.class,
                ExAlreadyExist.class,
                ExSharingRulesWarning.class,
                ExCannotResetPassword.class,
                ExNotAuthenticated.class,
                ExNotInvited.class));

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
        if (!_session.isAuthenticated()) {
            throw new ExNoPerm();
        }

        String sessionID = _session.id();
        l.info("Extend session: " + sessionID);

        _sessionExtender.extendSession(sessionID);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetUserPreferencesReply> getUserPreferences(@Nullable ByteString deviceId)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        FullName fn = user.getFullName();

        GetUserPreferencesReply.Builder reply = GetUserPreferencesReply.newBuilder()
                .setFirstName(fn._first)
                .setLastName(fn._last)
                .setSignupDate(user.getSignupDate())
                .setTwoFactorEnforced(user.shouldEnforceTwoFactor());

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
    public ListenableFuture<GetUserSettingsTokenReply> getUserSettingsToken()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        UserSettingsToken token = _factUserSettingsToken.create(user);

        GetUserSettingsTokenReply.Builder reply = GetUserSettingsTokenReply.newBuilder();

        // The token is optional. If the user does not have one, return null.
        if (token.exists()) {
            reply.setToken(token.get());
        }

        _sqlTrans.commit();

        return createReply(reply.build());
    }

    @Override
    public ListenableFuture<Void> setUserSettingsToken(String token)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        _factUserSettingsToken.save(user, token);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteUserSettingsToken()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        _factUserSettingsToken.create(user).delete();

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setUserPreferences(String userID, String firstName,
            String lastName, ByteString deviceId, String deviceName)
            throws Exception
    {
        boolean userNameUpdated = false;
        boolean deviceNameUpdated = false;

        _sqlTrans.begin();

        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userID);
        checkUserIsOrAdministers(requester, user);

        if (firstName != null || lastName != null) {
            if (firstName == null || lastName == null) {
                throw new ExBadArgs("First and last name must both be non-null or both null");
            }

            FullName fullName = FullName.fromExternal(firstName, lastName);
            l.info("{} set full name: {}, session user {}", user, fullName.getString(),
                    _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY));
            user.setName(fullName);
            userNameUpdated = true;
        }

        if (deviceId != null) {
            Device device = _factDevice.create(deviceId);
            throwIfNotOwner(user, device);

            // TODO (WW) print session user in log headers
            l.info("{} set device name: {}, session user {}", user, deviceName,
                    _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY));
            device.setName(deviceName);
            deviceNameUpdated = true;
        }

        // lipwig messages and command queue related stuff.
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

        // Wrap the jedis calls and lipwig pushes in the sql transaction so if any of the above
        // fail we can ask the user to perform the rename later.
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrganizationIDReply> getOrganizationID()
            throws Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        String orgID = user.getOrganization().id().toHexString();
        _sqlTrans.commit();

        GetOrganizationIDReply reply = GetOrganizationIDReply.newBuilder()
                                                             .setOrgId(orgID)
                                                             .build();

        return createReply(reply);

    }

    @Override
    public ListenableFuture<ListOrganizationMembersReply> listOrganizationMembers(
            Integer maxResults, Integer offset, String searchPrefix)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        Organization org = user.getOrganization();

        int userCount = searchPrefix != null ? org.countUsersWithPrefix(searchPrefix) : org.countUsers();

        ListOrganizationMembersReply reply = ListOrganizationMembersReply.newBuilder()
                .addAllUserAndLevel(
                        users2pbUserAndLevels(org.listUsers(maxResults, offset, searchPrefix)))
                .setTotalCount(userCount)
                .build();

        _sqlTrans.commit();

        return createReply(reply);
    }

    private static List<PBUserAndLevel> users2pbUserAndLevels(Collection<User> users)
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
    public ListenableFuture<SearchOrganizationUsersReply> searchOrganizationUsers(
            Integer maxResults, Integer offset, String searchPrefix)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        Organization org = user.getOrganization();

        SearchOrganizationUsersReply.Builder reply = SearchOrganizationUsersReply.newBuilder();
        for (User.EmailAndName u : org.searchAutocompleteUsers(maxResults, offset, searchPrefix)) {
            PBUser.Builder bd = PBUser.newBuilder()
                    .setUserEmail(u.email)
                    .setFirstName(u.firstName)
                    .setLastName(u.lastName);
            reply.addMatchingUsers(bd);
        }

        _sqlTrans.commit();

        return createReply(reply.build());
    }

    @Override
    public ListenableFuture<ListOrganizationSharedFoldersReply> listOrganizationSharedFolders(
            Integer maxResults, Integer offset, String searchPrefix)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        user.throwIfNotAdmin();
        Organization org = user.getOrganization();

        int sharedFolderCount = searchPrefix != null ? org.countSharedFoldersWithPrefix(searchPrefix): org.countSharedFolders();

        List<PBSharedFolder> pbs = sharedFolders2pb(org.listSharedFolders(maxResults, offset, searchPrefix), org, org.getTeamServerUser());

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

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        user.throwIfNotAdmin();

        ListOrganizationInvitedUsersReply.Builder builder =
                ListOrganizationInvitedUsersReply.newBuilder();
        for (OrganizationInvitation oi : user.getOrganization().getOrganizationInvitations()) {
            builder.addUserId(oi.getInvitee().id().getString());
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }


    public  ListenableFuture<ListSharedFoldersReply> listUserSharedFolders(String userID)
            throws Exception
    {
        return listUserSharedFolders(userID, null, null, null);
    }

    @Override
    public ListenableFuture<ListSharedFoldersReply> listUserSharedFolders(String userID,
          Integer maxResults, Integer offset, String searchPrefix)
            throws Exception
    {
        _sqlTrans.begin();

        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userID);
        checkUserIsOrAdministers(requester, user);

        int sharedFolderCount = searchPrefix != null ? user.countSharedFoldersWithPrefix(searchPrefix): user.countSharedFolders();

        List<PBSharedFolder> pbs = sharedFolders2pb(user.getSharedFolders(maxResults, offset, searchPrefix),
                requester.getOrganization(), user);

        _sqlTrans.commit();

        return createReply(ListSharedFoldersReply.newBuilder()
                .addAllSharedFolder(pbs)
                .setTotalCount(sharedFolderCount)
                .build());
    }

    @Override
    public ListenableFuture<ListUserDevicesReply> listUserDevices(String userID)
            throws ExNotAuthenticated, ExNoPerm, SQLException, ExInvalidID, ExNotFound,
            ExBadArgs, ExSecondFactorRequired, ExSecondFactorSetupRequired
    {
        _sqlTrans.begin();

        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userID);
        checkUserIsOrAdministers(requester, user);

        ListUserDevicesReply.Builder builder = ListUserDevicesReply.newBuilder();
        for (Device device : user.getDevices()) {
            builder.addDevice(PBDevice.newBuilder()
                    .setDeviceId(BaseUtil.toPB(device.id()))
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
    private void checkUserIsOrAdministers(User sessionUser, User target)
            throws SQLException, ExNotFound, ExNoPerm
    {
        if (target.equals(sessionUser)) return;

        // if the current user is different from the specified user, the current user must be
        // an admin of the organization the specified user belongs to.
        sessionUser.throwIfNotAdmin();

        // TODO (WW) use this string for all ExNoPerm's?
        String noPermMsg = "you don't have permission to perform this action";

        // Throw early if the specified user doesn't exist rather than relying on the following
        // below. This is to prevent attacker from testing user existence.
        if (!target.exists()) throw new ExNoPerm(noPermMsg);

        if (!target.belongsTo(sessionUser.getOrganization())) {
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
                    .setStoreId(BaseUtil.toPB(sf.id()))
                    .setName(sf.getName(user))
                    .setOwnedByTeam(false);

            // fill in folder members
            for (UserPermissionsAndState entry : sf.getUserRolesAndStatesForGroup(null)) {
                sharedFolderUser2pb(sessionOrg, user2nameCache, builder, entry._user,
                        entry._permissions, entry._state);
            }
            for (GroupPermissions entry : sf.getAllGroupsAndRoles()) {
                sharedFolderGroup2pb(builder, entry._group,
                        entry._permissions);
            }

            SharedFolderState requestedUserState = sf.getStateNullable(user);
            if (requestedUserState != null) {
                builder.setRequestedUsersPermissionsAndState(PBUserPermissionsAndState.newBuilder()
                        .setPermissions(sf.getPermissions(user).toPB())
                        .setState(requestedUserState.toPB())
                        .setUser(user2pb(user)));
            }

            pbs.add(builder.build());
        }
        return pbs;
    }

    private void sharedFolderUser2pb(Organization sessionOrg, Map<User, FullName> user2nameCache,
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

    private void sharedFolderGroup2pb(Builder builder, Group group, Permissions permissions)
            throws ExNotFound, SQLException
    {
        builder.addGroupPermissions(PBGroupPermissions.newBuilder()
                .setPermissions(permissions.toPB())
                .setGroup(group2pb(group)));
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
            fullname = getUserFullNameOrEmpty(user);
            user2nameCache.put(user, fullname);
        }

        return fullname;
    }

    // method to get the name of users that might not exist, i.e. shared folder members and group
    // members
    private static FullName getUserFullNameOrEmpty(User user)
            throws SQLException
    {
        try {
            return user.getFullName();
        } catch (ExNotFound e) {
            return new FullName("", "");
        }
    }

    private static PBUser.Builder user2pb(User user)
            throws SQLException, ExNotFound
    {
        return user2pb(user, getUserFullNameOrEmpty(user));
    }

    private static PBUser.Builder user2pb(User user, FullName fn)
            throws SQLException, ExNotFound
    {
        PBUser.Builder bd = PBUser.newBuilder()
                .setUserEmail(user.id().getString())
                .setFirstName(fn._first)
                .setLastName(fn._last);
        if (user.exists()) bd.setTwoFactorEnforced(user.shouldEnforceTwoFactor());
        return bd;
    }

    private static PBGroup.Builder group2pb(Group group)
            throws SQLException, ExNotFound
    {
        return PBGroup.newBuilder()
                .setGroupId(group.id().getInt())
                .setCommonName(group.getCommonName())
                .setExternallyManaged(group.isExternallyManaged());
    }

    @Override
    public ListenableFuture<Void> setAuthorizationLevel(final String userIdString,
            final PBAuthorizationLevel authLevel)
            throws Exception
    {
        _sqlTrans.begin();

        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
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

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

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

        final Organization org = _session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .getOrganization();

        final GetOrgPreferencesReply.Builder replyBuilder = GetOrgPreferencesReply.newBuilder()
                .setOrganizationName(org.getName())
                .setOrganizationContactPhone(org.getContactPhone())
                .setLevel(org.getTwoFactorEnforcementLevel().toPB())
                .setExternalUserCount(org.countExternalUsers())
                .setLicenseSeatsUsed(org.countInternalUsers());

        _sqlTrans.commit();

        return createReply(replyBuilder.build());
    }

    @Override
    public ListenableFuture<Void> setOrgPreferences(@Nullable final String orgName,
            @Nullable final String contactPhone)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
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
        AuthorizationLevel level = _session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .getLevel();
        _sqlTrans.commit();

        return createReply(GetAuthorizationLevelReply.newBuilder().setLevel(level.toPB()).build());
    }

    @Override
    public ListenableFuture<GetTeamServerUserIDReply> getTeamServerUserID()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

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
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE);
        Device device = _factDevice.create(deviceId);
        _sqlTrans.commit();

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
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Device device = _factDevice.create(deviceId);

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

    private static void throwIfNotOwner(User user, Device device)
            throws ExNotFound, SQLException, ExNoPerm
    {
        if (!device.getOwner().equals(user)) {
            throw new ExNoPerm("your are not the owner of the device");
        }
    }

    private static void throwIfNotOwner(Organization org, Group group)
            throws SQLException, ExNotFound, ExNoPerm
    {
        if (!org.equals(group.getOrganization())) {
            throw new ExNoPerm("your organization is not the owner of this group");
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
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE);

        throwIfNotAuthorizedToRegisterDevice(user.id(), osFamily, osName, deviceName,
                _remoteAddress.get(), interfaces);

        user.throwIfNotAdmin();
        User tsUser = user.getOrganization().getTeamServerUser();

        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
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
     * TODO rename this to something more appropriate.
     */
    @Override
    public ListenableFuture<Void> emailUser(String userId, String body)
            throws Exception
    {
        _sqlTrans.begin();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME,
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                        .id()
                        .getString(),
                null,
                UserID.fromExternal(userId).getString(),
                body,
                null);

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

        List<SubjectPermissions> srps = SubjectPermissionsList.listFromPB(subjectPermissionsList);

        _sqlTrans.begin();
        User sharer = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        l.info("{} shares {} [{}] with {}", sharer, sf, external, srps);

        ISharingRules rules = _sharingRules.create(sharer);

        List<AuditableEvent> events = Lists.newArrayList();
        boolean created = saveSharedFolderIfNecessary(folderName, sf, sharer, external);
        if (created) events.add(auditSharing(sf, sharer, "folder.create"));

        ImmutableCollection.Builder<UserID> affected = ImmutableSet.builder();

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are successfully committed to the
        // DB.
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        for (SubjectPermissions srp : srps) {
            if (srp._subject instanceof UserID) {
                User sharee = _factUser.create((UserID)srp._subject);
                Permissions actualPermissions = rules.onUpdatingACL(sf, sharee, srp._permissions);
                AffectedAndNeedsEmail updates = sf.addUserWithGroup(sharee, null, actualPermissions, sharer);
                affected.addAll(updates._affected);
                if (updates._needsEmail) {
                    emailers.add(
                            _invitationHelper.createFolderInvitationAndEmailer(sf, sharer, sharee,
                                    actualPermissions, note, folderName));
                }

                events.add(auditSharing(sf, sharer, "folder.invite")
                        .add("target", sharee.id())
                        .embed("role", actualPermissions.toArray()));
            } else if (srp._subject instanceof GroupID) {
                Group group = _factGroup.create((GroupID)srp._subject);

                // Permissions: cannot share to a group in a different organization. Sharing rules do
                // not have to be applied, as group sharing can only occur within an organization.
                throwIfNotOwner(sharer.getOrganization(), group);
                Permissions permissions = rules.onUpdatingACL(sf, group, srp._permissions);
                AffectedUserIDsAndInvitedUsers updates = group.joinSharedFolder(sf, permissions, sharer);
                affected.addAll(updates._affected);
                emailers.addAll(_invitationHelper.createFolderInvitationAndEmailer(
                        sf, sharer, updates._users, permissions, note, folderName));

                // Audit event for each invitation.
                for (User sharee : group.listMembers()) {
                    events.add(auditSharing(sf, sharer, "folder.invite")
                            .add("target", sharee.id())
                            .embed("role", permissions.toArray()));
                }
            } else {
                String msg = "The subject permissions list contains an invalid subject";
                l.warn("{}: {}", msg, srp._subject);
                throw new ExBadArgs(msg + ".");
            }
        }

        if (sf.getNumberOfActiveMembers() > SPParam.MAX_SHARED_FOLDER_MEMBERS) {
            throw new ExMemberLimitExceeded(sf.getNumberOfActiveMembers(),
                    SPParam.MAX_SHARED_FOLDER_MEMBERS);
        }

        if (!suppressSharingRulesWarnings) rules.throwIfAnyWarningTriggered();

        if (created || rules.shouldBumpEpoch()) {
            affected.addAll(sf.getJoinedUserIDs());
        }
        // Send lipwig notification as the last step of the transaction.
        _aclPublisher.publish_(affected.build());

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
        SharedFolder sf = _factSharedFolder.create(new SID(BaseUtil.fromPB(sid)));

        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

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

        if (state == SharedFolderState.LEFT) {
            l.info("Rejoining a left shared folder");
        }

        // Note 1. it also throws if the folder doesn't exist
        // Note 2. we allow users who have left the folder to rejoin
        if (state == null) throw new ExNotFound("No such invitation");

        // make user a member of the shared folder
        ImmutableCollection<UserID> users = sf.setState(user, SharedFolderState.JOINED);

        // set the external bit for consistent auto-join behavior across devices
        sf.setExternal(user, external);

        refreshCRLs(user);

        if (state == SharedFolderState.PENDING) {
            sendNotificationEmailToSharer(user, sf);
        }

        // always call this method as the last step of the transaction
        _aclPublisher.publish_(users);
    }

    private void sendNotificationEmailToSharer(User user, SharedFolder sf)
            throws SQLException, ExNotFound, IOException, MessagingException
    {
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
    }

    private void refreshCRLs(User user)
            throws SQLException, ExInvalidID, ExecutionException, InterruptedException
    {Collection<Device> peerDevices = user.getPeerDevices();
        // Refresh CRLs for peer devices once this user joins the shared folder (since the peer user
        // map may have changed).
        for (Device peer : peerDevices) {
            l.info("{} crl refresh", peer.id());
            _commandDispatcher.enqueueCommand(peer.id(), createCommandMessage(
                    CommandType.REFRESH_CRL));
        }
    }

    @Override
    public ListenableFuture<Void> ignoreSharedFolderInvitation(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        SharedFolder sf = _factSharedFolder.create(new SID(BaseUtil.fromPB(sid)));

        l.info(user + " ignore " + sf);

        // Note that it also throws if the folder or the user doesn't exist
        if (sf.getStateNullable(user) != SharedFolderState.PENDING) {
            throw new ExNotFound("No such invitation");
        }

        // Ignore the invitation by deleting the user.
        sf.removeIndividualUser(user);

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

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        SharedFolder sf = _factSharedFolder.create(new SID(BaseUtil.fromPB(sid)));

        l.info("{} leaves {}", user, sf);

        if (sf.id().isUserRoot()) throw new ExBadArgs("Cannot leave root folder");

        SharedFolderState state = sf.getStateNullable(user);

        if (state == null) throw new ExNotFound("No such folder or you are not a member of this shared folder");

        // Silently ignore leave call from pending users as multiple device of the same user
        // may make the call depending on the relative speeds of deletion propagation vs ACL
        // propagation.
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

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        SharedFolder sf = _factSharedFolder.create(new SID(BaseUtil.fromPB(sid)));

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
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
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
                    .setSid(BaseUtil.toPB(store.id()))
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
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        requester.throwIfNotAdmin();
        @Nullable Long quota = requester.getOrganization().getQuotaPerUser();
        _sqlTrans.commit();

        return createReply(
                quota == null ? GetQuotaReply.getDefaultInstance() : GetQuotaReply.newBuilder()
                        .setQuota(quota)
                        .build());
    }

    @Override
    public ListenableFuture<CreateUrlReply> createUrl(String soid)
            throws Exception
    {
        RestObject restObject;
        try {
            restObject = RestObject.fromString(soid);
        } catch (IllegalArgumentException e) {
            throw new ExBadArgs("invalid soid");
        }
        if (restObject.getSID() == null || restObject.getOID() == null) {
            throw new ExBadArgs("invalid soid");
        }
        SID sidToCheck = restObject.getOID().isAnchor() ?
                SID.anchorOID2storeSID(restObject.getOID()) : restObject.getSID();

        SharedFolder sf = _factSharedFolder.create(sidToCheck);
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        // Use throwIfNotJoinedOwner instead of throwIfNoPrivilegeToChangeACL. We do this because
        // we only want owners of shared folders to be able to create links. In particular, we do
        // not want org admins to be able to create links to arbitrary content.
        sf.throwIfNotJoinedOwner(requester);

        // Generate bifrost token.
        String accessCode = _accessCodeProvider.createAccessCodeForUser(requester);
        String token = _zelda.createAccessToken(soid, accessCode, 0);

        UrlShare link = _factUrlShare.save(restObject, token, requester.id());
        PBRestObjectUrl pbRestObjectUrl = link.toPB();
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.create")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", link.getKey())
                .add("soid", restObject.toStringFormal())
                .publish();

        return createReply(CreateUrlReply.newBuilder().setUrlInfo(pbRestObjectUrl).build());
    }

    public ListenableFuture<GetUrlInfoReply> getUrlInfo(String key)
            throws Exception
    {
        return getUrlInfo(key, null);
    }

    @Override
    public ListenableFuture<GetUrlInfoReply> getUrlInfo(String key, @Nullable ByteString password)
            throws Exception
    {
        l.debug("getUrlInfo {}", key);
        _sqlTrans.begin();

        if (password != null && _rateLimiter.update(_remoteAddress.get(), key)) {
            l.warn("rate limiter rejected getUrlInfo for {} from {}", key, _remoteAddress.get());
            throw new ExRateLimitExceeded();
        }

        UrlShare link = _factUrlShare.create(key);
        RestObject object = link.getRestObject();
        boolean hasPassword = link.hasPassword();
        boolean requireLogin = link.getRequireLogin();

        if (requireLogin) {
            User user = _session.getUserNullable();

            if (user == null) {
                l.info("getUrlInfo requires login; no user found");
                throw new ExNotAuthenticated();
            }
        }
        if (hasPassword) {
            try {
                User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
                _factSharedFolder.create(object.getSID()).throwIfNoPrivilegeToChangeACL(requester);
                l.info("getUrlInfo {} is admin/manager; not checking pwd", key);
            } catch (ExNoPerm | ExNotAuthenticated e) {
                l.info("getUrlInfo {} not admin/manager; checking pwd", key, LogUtil.suppress(e));
                if (password == null) throw new ExBadCredential();
                link.validatePassword(password.toByteArray());
            }
        }
        PBRestObjectUrl pbRestObjectUrl = link.toPB();
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.access")
                .add("ip", _remoteAddress.get())
                .add("key", key)
                .publish();

        return createReply(GetUrlInfoReply.newBuilder().setUrlInfo(pbRestObjectUrl).build());
    }

    @Override
    public ListenableFuture<Void> setUrlRequireLogin(String key, Boolean requireLogin)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        UrlShare link = _factUrlShare.create(key);
        RestObject soid = link.getRestObject();
        SharedFolder sf = _factSharedFolder.create(soid.getSID());
        sf.throwIfNoPrivilegeToChangeACL(requester);

        Long oldExpiry = link.getExpiresNullable();
        String accessCode = _accessCodeProvider.createAccessCodeForUser(requester);
        String newToken = _zelda.createAccessToken(soid.toStringFormal(), accessCode,
                firstNonNull(oldExpiry, 0L));
        _zelda.deleteToken(link.getToken());

        link.setRequireLogin(requireLogin, newToken);
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.set_require_login")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .add("require_login", requireLogin)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setUrlExpires(String key, Long expires)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        UrlShare link = _factUrlShare.create(key);
        RestObject soid = link.getRestObject();
        SharedFolder sf = _factSharedFolder.create(soid.getSID());
        sf.throwIfNoPrivilegeToChangeACL(requester);

        String accessCode = _accessCodeProvider.createAccessCodeForUser(requester);
        String newToken = _zelda.createAccessToken(soid.toStringFormal(), accessCode, expires);
        _zelda.deleteToken(link.getToken());

        link.setExpires(expires, newToken);
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.set_expiry")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .add("expiry", expires)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUrlExpires(String key)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        UrlShare link = _factUrlShare.create(key);
        RestObject soid = link.getRestObject();
        SharedFolder sf = _factSharedFolder.create(soid.getSID());
        sf.throwIfNoPrivilegeToChangeACL(requester);

        String accessCode = _accessCodeProvider.createAccessCodeForUser(requester);
        String newToken = _zelda.createAccessToken(soid.toStringFormal(), accessCode, 0);
        _zelda.deleteToken(link.getToken());

        link.removeExpires(newToken);
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.remove_expiry")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUrl(String key)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        UrlShare link = _factUrlShare.create(key);
        SID sid = link.getSid();
        SharedFolder sf = _factSharedFolder.create(sid);
        sf.throwIfNoPrivilegeToChangeACL(requester);

        _zelda.deleteToken(link.getToken());

        link.delete();
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.delete")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setUrlPassword(String key, ByteString password)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        UrlShare link = _factUrlShare.create(key);
        RestObject soid = link.getRestObject();
        SharedFolder sf = _factSharedFolder.create(soid.getSID());
        sf.throwIfNoPrivilegeToChangeACL(requester);

        Long oldExpiry = link.getExpiresNullable();
        String accessCode = _accessCodeProvider.createAccessCodeForUser(requester);
        String newToken = _zelda.createAccessToken(soid.toStringFormal(), accessCode,
                firstNonNull(oldExpiry, 0L));
        _zelda.deleteToken(link.getToken());

        link.setPassword(password.toByteArray(), newToken);
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.set_password")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> removeUrlPassword(String key)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        UrlShare link = _factUrlShare.create(key);
        SID sid = link.getSid();
        SharedFolder sf = _factSharedFolder.create(sid);
        sf.throwIfNoPrivilegeToChangeACL(requester);
        link.removePassword();
        _sqlTrans.commit();

        _auditClient.event(AuditTopic.LINK, "link.remove_password")
                .add("ip", _remoteAddress.get())
                .add("caller", requester.id().getString())
                .add("key", key)
                .publish();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> validateUrlPassword(String key, ByteString password)
            throws Exception
    {
        if (password != null && _rateLimiter.update(_remoteAddress.get(), key)) {
            l.warn("rate limiter rejected validateUrlPassword for {} from {}", key,
                    _remoteAddress.get());
            throw new ExRateLimitExceeded();
        }

        _sqlTrans.begin();
        UrlShare link = _factUrlShare.create(key);
        if (password == null) {
            throw new ExBadCredential();
        }
        link.validatePassword(password.toByteArray());
        _sqlTrans.commit();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<SetupTwoFactorReply> setupTwoFactor()
            throws Exception
    {
        _sqlTrans.begin();
        User requester =
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.TWO_FACTOR_SETUP);
        if (requester.shouldEnforceTwoFactor()) {
            requester.disableTwoFactorEnforcement();
            _sessionInvalidator.invalidateSecondFactor(requester.id());
            _auditClient.event(AuditTopic.USER, "user.2fa.disable")
                    .embed("caller", new AuditCaller(requester.id()))
                    .embed("user", requester.id())
                    .publish();
            _twoFactorEmailer.sendTwoFactorDisabledEmail(
                    requester.id().getString(), requester.getFullName()._first);
        }
        byte[] secret = requester.setupTwoFactor();
        SetupTwoFactorReply.Builder builder = SetupTwoFactorReply.newBuilder();
        builder.setSecret(ByteString.copyFrom(secret));
        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> setTwoFactorEnforcement(Boolean enforce, Integer currentCode,
            String userId)
            throws Exception
    {
        _sqlTrans.begin();
        User requester =
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.TWO_FACTOR_SETUP);
        // If we're trying to set a different user's second factor enforcement, our own session
        // must be fully authed (INTERACTIVE), not just TWO_FACTOR_SETUP.
        // Otherwise, a half-authed admin session could be able to disable other users' 2FA
        // enforcement.
        if (userId != null && !userId.equals(requester.id().getString())) {
            User.checkProvenance(requester, _session.getAuthenticatedProvenances(),
                    ProvenanceGroup.INTERACTIVE);
        }
        User target = (userId != null) ? _factUser.createFromExternalID(userId) : requester;
        checkUserIsOrAdministers(requester, target);
        // Noop happily unless there's actually a state change happening
        if (enforce != target.shouldEnforceTwoFactor()) {
            if (enforce) {
                // Only allow users to set up two-factor auth themselves.
                if (target != requester) {
                    throw new ExBadArgs("Not allowed to enable two-factor for other users");
                }
                // Verify the user provided a code
                if (currentCode == null) {
                    throw new ExBadArgs("No current two-factor code provided");
                }
                // Verify that currentCode matches our expectations for the user's current secret
                if (!target.checkSecondFactor(currentCode)) {
                    throw new ExBadCredential("Incorrect two-factor auth code");
                }
                // Mark this session as logged in with their second factor
                // (they have just proven that they possess the proper code)
                _session.setSecondFactorAuthDate(System.currentTimeMillis());
                target.enableTwoFactorEnforcement();
                _auditClient.event(AuditTopic.USER, "user.2fa.enable")
                        .embed("caller", new AuditCaller(requester.id()))
                        .embed("user", target.id())
                        .publish();
                _twoFactorEmailer.sendTwoFactorEnabledEmail(target.id().getString(),
                        target.getFullName()._first);
            } else {
                target.disableTwoFactorEnforcement();
                _sessionInvalidator.invalidateSecondFactor(target.id());
                _auditClient.event(AuditTopic.USER, "user.2fa.disable")
                        .embed("caller", new AuditCaller(requester.id()))
                        .embed("user", target.id())
                        .publish();
                _twoFactorEmailer.sendTwoFactorDisabledEmail(target.id().getString(),
                        target.getFullName()._first);
            }
        }
        _sqlTrans.commit();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> setTwoFactorSetupEnforcement(PBTwoFactorEnforcementLevel pblevel)
            throws Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE);
        user.throwIfNotAdmin();
        Organization org = user.getOrganization();
        TwoFactorEnforcementLevel level = TwoFactorEnforcementLevel.fromPB(pblevel);
        TwoFactorEnforcementLevel oldLevel = org.getTwoFactorEnforcementLevel();
        org.setTwoFactorEnforcementLevel(level);
        if (!oldLevel.equals(level)) {
            _auditClient.event(AuditTopic.ORGANIZATION, "org.2fa.level")
                    .embed("caller", new AuditCaller(user.id()))
                    .embed("org", org.id().toTeamServerUserID())
                    .embed("old_level", oldLevel.toString())
                    .embed("new_level", level.toString())
                    .publish();
        }
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetTwoFactorSetupEnforcementReply> getTwoFactorSetupEnforcement()
            throws Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.TWO_FACTOR_SETUP);
        Organization org = user.getOrganization();
        TwoFactorEnforcementLevel level = org.getTwoFactorEnforcementLevel();
        _sqlTrans.commit();

        GetTwoFactorSetupEnforcementReply.Builder reply =
                GetTwoFactorSetupEnforcementReply.newBuilder()
                        .setLevel(level.toPB());
        return createReply(reply.build());
    }

    @Override
    public ListenableFuture<Void> destroySharedFolder(ByteString sharedId)
            throws Exception
    {
        _sqlTrans.begin();
        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        SharedFolder sf = _factSharedFolder.create(new SID(BaseUtil.fromPB(sharedId)));
        l.info("{} destroys {}", caller, sf);
        if (!sf.exists()) throw new ExNotFound("The folder does not exist");
        if (sf.id().isUserRoot()) throw new ExBadArgs("Cannot leave root folder");
        sf.throwIfNoPrivilegeToChangeACL(caller);
        String folderName = sf.getName(caller);
        ImmutableCollection<UserID> affectedUsers = sf.destroy();

        _auditClient.event(AuditTopic.SHARING, "folder.destroy")
                .embed("folder", new AuditFolder(sf.id(), folderName))
                .embed("caller", new AuditCaller(caller.id()))
                .publish();

        // this must be the last thing in the transaction
        _aclPublisher.publish_(affectedUsers);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPriorityDefectEmail(
            String defectID,
            String contactEmail,
            String subject,
            String message)
            throws Exception
    {
        _sqlTrans.begin();
        UserID userID = _session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .id();
        _sqlTrans.commit();

        throwOnInvalidEmailAddress(contactEmail);

        String fromName = SPParam.EMAIL_FROM_NAME;
        String to = WWW.SUPPORT_EMAIL_ADDRESS;
        String header = format("%s Support", SPParam.BRAND);
        String body;
        String collectLogsUrl = getStringProperty("base.collect_logs.url");

        String link = format("%s?defect_id=%s&email=%s&users=%s&subject=%s&message=%s#client",
                collectLogsUrl,
                urlEncode(defectID),
                urlEncode(contactEmail),
                urlEncode(userID.getString()),
                urlEncode(subject),
                urlEncode(message));
        body = format("A user has reported a problem using %s.\n\nUser: %s\nSubject: %s\nMessage: %s\n\n" +
                      "Use the following link to collect logs from this user and optionally submit them to %s Support:\n\n%s\n\n",
                SPParam.BRAND, contactEmail, subject, message, SPParam.BRAND, link);

        Email email = new Email();
        email.addSection(header, body);
        _emailSender.sendPublicEmailFromSupport(
                fromName,
                to,
                contactEmail,
                format("[%s Support] %s", SPParam.BRAND, subject),
                email.getTextEmail(),
                email.getHTMLEmail());

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListUrlsForStoreReply> listUrlsForStore(ByteString sharedId)
            throws Exception
    {
        ListUrlsForStoreReply.Builder builder = ListUrlsForStoreReply.newBuilder();
        _sqlTrans.begin();
        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        SID sid = sharedId.isValidUtf8() && sharedId.toStringUtf8().equals("root") ?
                SID.rootSID(caller.id()) : new SID(BaseUtil.fromPB(sharedId));

        SharedFolder sf = _factSharedFolder.create(sid);
        if (!sf.exists()) throw new ExNotFound("The folder does not exist");
        sf.throwIfNoPrivilegeToChangeACL(caller);
        for (UrlShare url : _factUrlShare.getAllInStore(sf.id())) {
            builder.addUrl(url.toPB());
        }
        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> provideSecondFactor(Integer currentCode)
            throws Exception
    {
        _sqlTrans.begin();
        // Verify that this session has specifically the BASIC provenance
        // (no two factor for certs)
        if (!_session.getAuthenticatedProvenances().contains(Provenance.BASIC)) {
            throw new ExNotAuthenticated();
        }
        User requester = _session.getUserNullable();
        boolean exceededRateLimit = _rateLimiter.update(_session.id());
        if (exceededRateLimit) {
            l.warn("second factor exceeded ratelimit");
            throw new ExRateLimitExceeded();
        } else if (!requester.checkSecondFactor(currentCode)) {
            // failed, incorrect code
            l.warn("2fa failed: incorrect code {} for {}", currentCode, requester);
            throw new ExSecondFactorRequired();
        } else {
            _session.setSecondFactorAuthDate(System.currentTimeMillis());
            l.info("{} 2fa login succeeded", requester);
        }
        _sqlTrans.commit();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> provideBackupCode(String backupCode)
            throws Exception
    {
        _sqlTrans.begin();
        // Verify that this session has specifically the BASIC provenance
        // (no two factor for certs)
        if (!_session.getAuthenticatedProvenances().contains(Provenance.BASIC)) {
            throw new ExNotAuthenticated();
        }
        User requester = _session.getUserNullable();
        boolean exceededRateLimit = _rateLimiter.update(_session.id());
        if (exceededRateLimit) {
            l.warn("backup code login attempts exceeded ratelimit");
            throw new ExRateLimitExceeded();
        } else if (!requester.checkBackupCode(backupCode)) {
            // failed, incorrect code
            l.warn("2fa failed: incorrect backup code {} for {}", backupCode, requester);
            throw new ExSecondFactorRequired();
        } else {
            _session.setSecondFactorAuthDate(System.currentTimeMillis());
            l.info("{} 2fa login with backup code succeeded", requester);
        }
        _sqlTrans.commit();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetBackupCodesReply> getBackupCodes()
            throws Exception
    {
        _sqlTrans.begin();
        User requester =
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE);
        GetBackupCodesReply.Builder builder = GetBackupCodesReply.newBuilder();
        for (RecoveryCode code : requester.recoveryCodes()) {
            builder.addCodes(code.toPB());
        }
        _sqlTrans.commit();
        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> setQuota(Long quota)
            throws Exception
    {
        _sqlTrans.begin();
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
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
        User requester = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
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

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        Collection<PendingSharedFolder> psfs = user.getPendingSharedFolders();

        ListPendingFolderInvitationsReply.Builder builder =
                ListPendingFolderInvitationsReply.newBuilder();
        for (PendingSharedFolder psf : psfs) {
            builder.addInvitation(PBFolderInvitation.newBuilder()
                    .setShareId(BaseUtil.toPB(psf._sf.id()))
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
        throwOnInvalidEmailAddress(emailAddress);

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
            // The user already exists. Don't return special messages (errors, warnings, etc)
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

        User inviter = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User invitee = _factUser.create(UserID.fromExternal(userIdString));
        Organization org = inviter.getOrganization();

        l.info("{} sends organization invite to {}", inviter, invitee);

        InvitationEmailer emailer;
        if (!invitee.exists()) {
            // The user doesn't exist. Send him a sign-up invitation email only, and associate the
            // signup code with the organization invitation. See signUpWithCode() on how this association is
            // consumed.
            InviteToSignUpResult res = _invitationHelper.inviteToSignUp(inviter, invitee);
            // ignore the emailer returned by inviteToOrganization(), so we only send one email
            // rather than two.
            inviteToOrganization(inviter, invitee, org, res._signUpCode);
            emailer = res._emailer;
        } else {
            throw new ExAlreadyExist(invitee + " is already a member of the organization");
        }

        boolean locallyManaged = _authenticator.isLocallyManaged(invitee.id());

        _sqlTrans.commit();

        // send the email after transaction since it may take some time and it's okay to fail
        emailer.send();

        return createReply(InviteToOrganizationReply.newBuilder()
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
            throws ExAlreadyExist, SQLException, ExNotFound, IOException
    {
        OrganizationInvitation invite = _factOrgInvite.create(invitee, org);

        if (invite.exists()) {
            _factOrgInvite.update(inviter, invitee, org);
        } else {
            _factOrgInvite.save(inviter, invitee, org, signUpCode);
        }


        _auditClient.event(AuditTopic.USER, "user.org.invite")
                .add("inviter", inviter.id())
                .add("invitee", invitee.id())
                .publish();

        return _factInvitationEmailer.createOrganizationInvitationEmailer(inviter, invitee);
    }

    @Override
    public ListenableFuture<Void> acceptOrganizationInvitation(Integer orgID)
            throws Exception
    {
        throw new UnsupportedOperationException("Cross-team invitations do not exist. How did you get here?");
    }

    @Override
    public ListenableFuture<Void> deleteOrganizationInvitation(
            Integer orgID)
            throws SQLException, ExNotAuthenticated, ExNotFound, ExSecondFactorRequired,
            ExSecondFactorSetupRequired
    {
        throw new UnsupportedOperationException("Cross-team invitations do not exist. How did you get here?");
    }

    @Override
    public ListenableFuture<Void> deleteOrganizationInvitationForUser(
            String userID) throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();

        User invitee = _factUser.createFromExternalID(userID);
        _factOrgInvite.create(invitee, org).delete();

        /**
         * Remove the user's sign-up code as well as reminder emails so they will no longer be signed
         * up to the system at all. See also sign_up_workflow.md. Strictly, this is necessary only
         * for locally managed users (see Authenticator.isLocallyManaged(), but doing so for
         * externally managed users doesn't have side effects (no sign-up codes are ever generated
         * for these users).
         *
         * See also docs/design/sign_up_workflow.md.
         *
         * N.B. we implicitly assumed that there is only one organization invitation for this
         * user in private deployment because:
         *
         * 1. there can only be one invitation to an user from each organization.
         * 2. there is only one organization in private deployment.
         *
         * Note that single invitation was deleted above, so it should be safe to delete
         * all sign up codes for this user.
         */
        invitee.deleteAllSignUpCodes();
        _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        _sqlTrans.commit();

        return createVoidReply();
    }

    // this call was previously RemoveUserFromOrganization
    @Override
    public ListenableFuture<Void> noop() throws Exception
    {
        throw new ExNotFound("This call has been removed.");
    }

    @Override
    public ListenableFuture<RecertifyDeviceReply> recertifyDevice(ByteString deviceId,
            ByteString csr)
            throws Exception
    {
        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Device device = _factDevice.create(deviceId);
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
        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Device device = _factDevice.create(deviceId);
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

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm, ExNotAuthenticated, ExNotFound, ExSecondFactorRequired,
            ExSecondFactorSetupRequired
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        GetACLReply.Builder bd = GetACLReply.newBuilder();

        l.info("getACL for {}", user.id());

        long serverEpoch = user.getACLEpoch();
        if (serverEpoch == epoch) {
            l.info("no updates - matching epoch: {}", epoch);
        } else {
            for (SharedFolder sf : user.getJoinedFolders()) {
                l.debug("add store {}", sf.id());
                PBStoreACL.Builder aclBuilder = PBStoreACL.newBuilder();
                aclBuilder.setStoreId(BaseUtil.toPB(sf.id()));
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
        Object userOrGroupID = SubjectPermissions.getSubjectFromString(subjectString);
        Permissions role = Permissions.fromPB(permissions);
        SharedFolder sf = _factSharedFolder.create(storeId);
        ImmutableCollection.Builder<UserID> affected = ImmutableSet.builder();

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update)

        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        sf.throwIfNoPrivilegeToChangeACL(user);
        ISharingRules rules = _sharingRules.create(user);

        if (userOrGroupID instanceof UserID) {
            User subject = _factUser.create((UserID)userOrGroupID);
            Permissions oldPermissions = sf.getPermissionsInGroup(subject, null);
            if (oldPermissions != role) {
                role = rules.onUpdatingACL(sf, subject, role);
                if (!suppressWarnings) rules.throwIfAnyWarningTriggered();

                affected.addAll(changeRoleSendEmailAndAudit(sf, user, subject, oldPermissions, role));

            }
        } else if (userOrGroupID instanceof GroupID) {
            Group subject = _factGroup.create((GroupID)userOrGroupID);
            Permissions oldPermissions = subject.getRoleForSharedFolder(sf);
            if (oldPermissions != role) {
                role = rules.onUpdatingACL(sf, subject, role);
                if (!suppressWarnings) rules.throwIfAnyWarningTriggered();

                affected.addAll(changeRoleSendEmailAndAudit(sf, user, subject, oldPermissions, role));
            }
        } else {
            String msg = "The subject permissions list contains an invalid subject";
            l.warn("{}: {}", msg, subjectString);
            throw new ExBadArgs(msg + ".");
        }

        if (rules.shouldBumpEpoch()) {
            affected.addAll(sf.getJoinedUserIDs());
        }
        _aclPublisher.publish_(affected.build());
        _sqlTrans.commit();

        return createVoidReply();
    }

    private ImmutableCollection<UserID> changeRoleSendEmailAndAudit(SharedFolder sf, User changer,
            User changee, Permissions oldRole, Permissions newRole)
            throws SQLException, ExNoAdminOrOwner, ExNotFound, IOException, MessagingException
    {
        //by this point we've already determined that the ACL exists, getPermissionsNullable should
        //never return null
        Permissions oldEffectivePermissions = sf.getPermissionsNullable(changee);
        ImmutableCollection<UserID> affected = sf.setPermissions(changee, newRole);
        Permissions newEffectivePermissions = sf.getPermissionsNullable(changee);

        _sfnEmailer.sendRoleChangedNotificationEmail(sf, changer, changee, oldEffectivePermissions,
                newEffectivePermissions);

        auditSharing(sf, changer, "folder.permission.update")
                .add("target", changee.id())
                .embed("new_role", newRole.toArray())
                .embed("old_role", oldRole.toArray())
                .publish();

        return affected;
    }

    private ImmutableCollection<UserID> changeRoleSendEmailAndAudit(SharedFolder sf, User changer,
            Group changee, Permissions oldRole, Permissions newRole)
            throws ExNoAdminOrOwner, SQLException, ExNotFound, IOException, MessagingException
    {
        ImmutableCollection<UserID> affected = changee.changeRoleInSharedFolder(sf, newRole);

        _sfnEmailer.sendRoleChangedNotificationEmail(sf, changer, changee, oldRole,
                newRole);

        auditSharing(sf, changer, "folder.permission.update")
                .add("target", changee.id())
                .embed("new_role", newRole.toArray())
                .embed("old_role", oldRole.toArray())
                .publish();

        return affected;
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId, String subjectString)
            throws Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        SharedFolder sf = _factSharedFolder.create(storeId);
        sf.throwIfNoPrivilegeToChangeACL(user);
        Object userOrGroupID = SubjectPermissions.getSubjectFromString(subjectString);
        if (userOrGroupID instanceof UserID) {
            User subject = _factUser.create((UserID)userOrGroupID);

            auditSharing(sf, user, "folder.permission.delete").add("target", subject.id())
                    .publish();

            _aclPublisher.publish_(sf.removeIndividualUser(subject));
            _sfnEmailer.sendRemovedFromFolderNotificationEmail(sf, user, subject);
        } else if (userOrGroupID instanceof GroupID) {
            Group subject = _factGroup.create((GroupID)userOrGroupID);

            auditSharing(sf, user, "folder.permission.delete").add("target", subject.id())
                    .publish();

            // always call this method as the last step of the transaction
            _aclPublisher.publish_(subject.deleteSharedFolder(sf));
            _sfnEmailer.sendRemovedFromFolderNotificationEmail(sf, user, subject);
        } else {
            String msg = "The subject permissions list contains an invalid subject";
            l.warn("{}: {}", msg, subjectString);
            throw new ExBadArgs(msg + ".");
        }
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signOut()
            throws Exception
    {
        _session.deauthorize();
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

        // On password reset we expect all web sessions to get logged out. Invalidating all sessions
        // in SP makes this happen.
        _sessionInvalidator.invalidate(user.id());

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
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE).id(),
                old_credentials.toByteArray(), new_credentials.toByteArray());
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
                .add("is_admin", !joinExistingOrg) // TODO: this is incorrect if the user already exists
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
     * @throws ExBadArgs if the user id is empty
     * @throws com.aerofs.base.ex.ExLicenseLimit if a seat limit prevents this new user from signing in
     * @throws ExBadCredential if username/password combination is incorrect
     */
    @Override
    public ListenableFuture<SignInUserReply> signInUser(String userId, ByteString credentials)
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
        _session.setUser(user);
        _session.setBasicAuthDate(System.currentTimeMillis());

        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _session.id());

        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", user.id())
                .add("authority", authority)
                .publish();

        _sqlTrans.begin();
        Organization org = user.getOrganization();
        TwoFactorEnforcementLevel level = org.getTwoFactorEnforcementLevel();
        boolean enforceTwoFactor = user.shouldEnforceTwoFactor() &&
                (level != TwoFactorEnforcementLevel.DISALLOWED);
        boolean needSecondFactorSetup = (level == TwoFactorEnforcementLevel.MANDATORY) &&
                !enforceTwoFactor;
        boolean needSecondFactor = enforceTwoFactor && !_session.getAuthenticatedProvenances()
                .contains(Provenance.BASIC_PLUS_SECOND_FACTOR);
        _sqlTrans.commit();

        return createReply(SignInUserReply.newBuilder()
                .setNeedSecondFactor(needSecondFactor)
                .setNeedSecondFactorSetup(needSecondFactorSetup)
                .build());
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
     * @throws ExBadArgs if the user id is empty
     * @throws ExBadCredential if username/credential combination is incorrect
     */
    @Override
    public ListenableFuture<SignInUserReply> credentialSignIn(String userId, ByteString credentials)
            throws Exception
    {
        User user = authByCredentials(userId, credentials);
        _session.setUser(user);
        _session.setBasicAuthDate(System.currentTimeMillis());
        _userTracker.signIn(user.id(), _session.id());
        _sqlTrans.begin();

        Organization org = user.getOrganization();
        TwoFactorEnforcementLevel level = org.getTwoFactorEnforcementLevel();
        boolean enforceTwoFactor = user.shouldEnforceTwoFactor() &&
                (level != TwoFactorEnforcementLevel.DISALLOWED);
        boolean needSecondFactorSetup = (level == TwoFactorEnforcementLevel.MANDATORY) &&
                !enforceTwoFactor;
        boolean needSecondFactor = enforceTwoFactor && !_session.getAuthenticatedProvenances()
                .contains(Provenance.BASIC_PLUS_SECOND_FACTOR);
        Timestamp passwordCreatedTS = user.getPasswordCreatedTS();
        _sqlTrans.commit();

        Calendar calendar = Calendar.getInstance();
        Timestamp currentTS = new Timestamp(calendar.getTime().getTime());
        long tsDiffInMillis = currentTS.getTime() - passwordCreatedTS.getTime();
        int expirationPeriodMonths = getIntegerProperty("password.restriction.expiration_period_months", 0);
        double NUM_MILLISECONDS_IN_YEAR = 31556952000D;

        //Convert password expiration period from months to milliseconds
        double expirationPeriodMonthsInMillis = expirationPeriodMonths * NUM_MILLISECONDS_IN_YEAR/12;

        // Check if user's password is expired (in the event that password expiry is set and user is locally managed)
        if (tsDiffInMillis > expirationPeriodMonthsInMillis && expirationPeriodMonths != 0
                && _authenticator.isLocallyManaged(user.id())){
            l.info("Password expired for " + user);
            throw new ExPasswordExpired();
        }

        return createReply(SignInUserReply.newBuilder()
                    .setNeedSecondFactor(needSecondFactor)
                    .setNeedSecondFactorSetup(needSecondFactorSetup)
                    .build());
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
        if (_rateLimiter.update(_remoteAddress.get(), userId)) {
            l.warn("rate limiter rejected credential sign in for {} from {}", userId,
                    _remoteAddress.get());
            throw new ExRateLimitExceeded();
        }
        User user = _factUser.createFromExternalID(userId);
        Organization org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
        OrganizationInvitation invite = _factOrgInvite.create(user, org);

        _sqlTrans.begin();

        //externally-managed account will need to have an account in user database or
        //a pending invitation to log in
        boolean userExistsOrInviteExists = (user.exists() || invite.exists());
        _sqlTrans.commit();

        // Check whether AD/LDAP users are required to be invited before logging in.
        if (!userExistsOrInviteExists && externalUserRequireInvitation() &&
                !_authenticator.isLocallyManaged(user.id())) {
            throw new ExNotInvited();
        }

        IAuthority authority = _authenticator.authenticateUser(
                user, cred.toByteArray(), _sqlTrans, CredentialFormat.TEXT);

        l.info("SI: cred auth ok {}", user.id().getString());
        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", user.id())
                .add("authority", authority)
                .publish();

        return user;
    }

    private boolean externalUserRequireInvitation() {
        return Identity.convertProperty("lib.authenticator", "local_credential")
                == Identity.Authenticator.EXTERNAL_CREDENTIAL
                && getBooleanProperty("ldap.invitation.required_for_signup", false);
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
        } catch (ExInvalidID e) {
            l.error(user + ": did malformed");
            throw new ExBadCredential();
        }

        _sqlTrans.begin();

        user.throwIfBadCertificate(_certauth, device);
        _session.setUser(user);
        _session.setCertificateAuthDate(System.currentTimeMillis());
        _userTracker.signIn(user.id(), _session.id());

        _sqlTrans.commit();

        // Do not remove this event! It's important!
        //
        // This audit event is temporarily being used by Bloomberg to identify a device's last
        // known IP address for the purpose of detecting file transfers across network boundaries.
        // We rely on the ACL Synchronizer to reconnect to SP and sign in using the device
        // certificate on lipwig reconnect.
        _auditClient.event(AuditTopic.DEVICE, "device.signin")
                .add("user", user.id())
                .add("device_id", device.id().toStringFormal())
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
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        boolean userExists = _session.isAuthenticated() && user.exists();
        _sqlTrans.commit();

        if (!userExists) throw new ExNotFound("Attempt to create device auth for non-existent user");
        return createReply(MobileAccessCode.newBuilder()
                .setAccessCode(_accessCodeProvider.createAccessCodeForUser(user))
                .build());
    }

    /**
     * Authorize an API client (mobile, Web access, and what not) by providing a device
     * authorization nonce previously generated by a signed-in user. The nonce is auto-deleted on
     * first use (it is also self-destructing after a short time).
     *
     * If the nonce is invalid, or it refers to a non-existing user, this method will throw
     * an appropriate exception.
     *
     * @throws ExBadCredential the nonce refers to a non-existing user
     * @throws com.aerofs.base.ex.ExExternalAuthFailure the nonce does not exist (used or expired)
     */
    @Override
    public ListenableFuture<AuthorizeAPIClientReply> authorizeAPIClient(String nonce,
            String deviceName)
            throws Exception
    {
        if (_session.isAuthenticated()) throw new ExNoPerm("User/device session state conflict");

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

        ListenableFuture<AuthorizeAPIClientReply> reply = createReply(
                AuthorizeAPIClientReply.newBuilder()
                        .setUserId(user.id().getString())
                        .setOrgId(user.getOrganization().id().toString())
                        .setIsOrgAdmin(user.isAdmin())
                        .build());
        _sqlTrans.commit();

        l.info("SI: authorized device for {}", user.id().getString());

        _auditClient.event(AuditTopic.DEVICE, "device.certify")
                .add("user", user.id())
                .add("device_type", "API Client")
//                .add("device_id", deviceName) // FIXME: Get this from somewhere?
//                .add("os_family", "unknown")  // FIXME: Get this from somewhere?
//                .add("os_name", "unknown")    // FIXME: Get this from somewhere?
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
        Organization org = user.getOrganization();
        TwoFactorEnforcementLevel level = org.getTwoFactorEnforcementLevel();
        boolean enforceSecondFactor = user.shouldEnforceTwoFactor() &&
                level != TwoFactorEnforcementLevel.DISALLOWED;
        _sqlTrans.commit();
        l.info("SI (OpenID): " + user.toString());

        // Set the session cookie.
        _session.setUser(user);
        _session.setBasicAuthDate(System.currentTimeMillis());

        // Check if the user will need to set up a second factor per organization policy
        boolean needSecondFactorSetup = (level == TwoFactorEnforcementLevel.MANDATORY) &&
                !enforceSecondFactor;

        // Check if the user will need to provide a second factor to use this session
        boolean needSecondFactor = enforceSecondFactor && !_session.getAuthenticatedProvenances()
                .contains(Provenance.BASIC_PLUS_SECOND_FACTOR);

        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _session.id());

        _auditClient.event(AuditTopic.USER, "user.signin")
                .add("user", attrs.getEmail())
                .add("authority", "OpenId")
                .publish();

        return createReply(OpenIdSessionAttributes.newBuilder()
                .setUserId(attrs.getEmail())
                .setFirstName(attrs.getFirstName())
                .setLastName(attrs.getLastName())
                .setNeedSecondFactor(needSecondFactor)
                .setNeedSecondFactorSetup(needSecondFactorSetup)
                .build());
    }

    @Override
    public ListenableFuture<Void> noop2(final Long crlEpoch)
        throws Exception
    {
        throw new ExProtocolError("GetUserCRL has been removed");
    }

    @Override
    public ListenableFuture<Void> noop3()
        throws Exception
    {
        throw new ExProtocolError("GetCRL has been removed");
    }

    @Override
    public ListenableFuture<Void> unlinkDevice(final ByteString deviceId, Boolean erase)
        throws Exception
    {
        _sqlTrans.begin();

        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Device device = _factDevice.create(deviceId);
        checkUserIsOrAdministers(user, device.getOwner());

        // TODO (WW) print session user in log headers
        l.info("{} unlinks {}, erase {}, session user {}", user, device.id().toStringFormal(),
                erase, _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY));

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
        device.throwIfNotOwner(_session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY));
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

        Set<UserID> sharedUsers = _db.getSharedUsersSet(
                _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY).id());

        GetDeviceInfoReply.Builder builder = GetDeviceInfoReply.newBuilder();
        for (ByteString did : dids) {
            Device device = _factDevice.create(did);

            // If there is a permission error or the device does not exist, simply provide an empty
            // device info object.
            if (device.exists()) {
                User owner = device.getOwner();
                if (sharedUsers.contains(owner.id())) {
                    builder.addDeviceInfo(GetDeviceInfoReply.PBDeviceInfo.newBuilder()
                            .setDeviceName(device.getName())
                            .setOwner(PBUser.newBuilder()
                                    .setUserEmail(owner.id().getString())
                                    .setFirstName(owner.getFullName()._first)
                                    .setLastName(owner.getFullName()._last)));
                } else {
                    builder.addDeviceInfo(EMPTY_DEVICE_INFO);
                }
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
        _sqlTrans.begin();
        User sessionUser = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        ImmutableList.Builder<SharedFolder> foldersBuilder = ImmutableList.builder();

        for (ByteString sid : sids) {
            SharedFolder folder = _factSharedFolder.create(sid);
            if (folder.getPermissionsNullable(sessionUser) == null) throw new ExNoPerm();
            foldersBuilder.add(folder);
        }

        List<PBSharedFolder> pbFolders = sharedFolders2pb(foldersBuilder.build(),
                sessionUser.getOrganization(), sessionUser);
        _sqlTrans.commit();

        return createReply(
                ListSharedFoldersReply.newBuilder().addAllSharedFolder(pbFolders).build());
    }

    @Override
    public ListenableFuture<Void> addUserToWhitelist(final String userEmail)
            throws Exception
    {
        _sqlTrans.begin();
        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userEmail);

        l.debug("{} add {} to whitelist", caller.id().getString(), user.id().getString());

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
        _sqlTrans.begin();

        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userEmail);

        l.debug("{} remove {} from whitelist", caller.id().getString(), user.id().getString());

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
        _sqlTrans.begin();
        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);

        l.debug("list whitelisted users: {}", caller.id().getString());

        ListWhitelistedUsersReply.Builder builder = ListWhitelistedUsersReply.newBuilder();

        caller.throwIfNotAdmin();  // throws ExNoPerm
        for (User user : caller.getOrganization().listWhitelistedUsers()) {
            builder.addUser(user2pb(user)).build();
        }
        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> deactivateUser(String userId, Boolean eraseDevices)
            throws Exception
    {
        _sqlTrans.begin();
        User caller = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        User user = _factUser.createFromExternalID(userId);
        user.getOrganization();

        UserManagement.deactivateByAdmin(caller, user, eraseDevices, _commandDispatcher,
                _aclPublisher);

        _sqlTrans.commit();

        _userTracker.signOutAll(user.id());

        return createVoidReply();
    }

    @Override
    public ListenableFuture<CreateGroupReply> createGroup(String commonName) throws
            SQLException,
            ExNotAuthenticated,
            ExSecondFactorRequired,
            ExNotFound,
            ExNoPerm,
            ExSecondFactorSetupRequired
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        // Permissions: cannot create a group unless you are an admin.
        user.throwIfNotAdmin();
        Group group = _factGroup.save(commonName, user.getOrganization().id(), null);
        _sqlTrans.commit();

        l.info("{} created {}; common name {}", user, group, commonName);

        return createReply(CreateGroupReply.newBuilder().setGroupId(group.id().getInt()).build());
    }

    @Override
    public ListenableFuture<Void> setGroupCommonName(Integer groupID, String commonName) throws
            SQLException,
            ExNotAuthenticated,
            ExSecondFactorRequired,
            ExNotFound,
            ExNoPerm,
            ExNotLocallyManaged,
            ExSecondFactorSetupRequired,
            ExBadArgs
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        // Permissions: cannot modify a group unless you are an admin.
        user.throwIfNotAdmin();
        Group group = _factGroup.create(groupID);
        // Permissions: cannot modify a group unless it is owned by your organization.
        throwIfNotOwner(user.getOrganization(), group);
        // Permissions: can only modify an external group's common name if we couldn't find its CN
        // from the LDAP tree - valid CN's can also contain an escaped "=", so the condition is not
        // strictly correct, but this code is only to prevent user confusion
        if (group.isExternallyManaged() && !group.getCommonName().contains("=")) {
            throw new ExNotLocallyManaged("only allowed to change name of externally managed " +
                    "group if we could not get its name from the LDAP endpoint");
        }
        group.setCommonName(commonName);
        _sqlTrans.commit();

        l.info("{} modified {}; new common name {}", user, group, commonName);

        return createReply(Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Void> addGroupMembers(Integer groupID, List<String> userEmails)
            throws
            Exception
    {
        ImmutableCollection.Builder<UserID> needsACLUpdate = ImmutableSet.builder();
        _sqlTrans.begin();
        User admin = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Organization org = admin.getOrganization();
        // Permissions: cannot modify a group unless you are an admin.
        admin.throwIfNotAdmin();
        Group group = _factGroup.create(groupID);
        // Permissions: cannot modify a group unless it is owned by your organization.
        throwIfNotOwner(org, group);
        // Permissions: cannot modify externally managed groups.
        group.throwIfExternallyManaged();

        if (group.listMembers().size() + userEmails.size() > SPParam.MAX_GROUP_SIZE) {
            throw new ExMemberLimitExceeded(group.listMembers().size() + userEmails.size(),
                    SPParam.MAX_GROUP_SIZE);
        }

        List<InvitationEmailer> emails = Lists.newLinkedList();
        for (String userEmail : userEmails) {
            User newMember = _factUser.create(UserID.fromExternal(userEmail));

            if (!_authenticator.isInternalUser(newMember.id())) {
                throw new ExWrongOrganization(newMember + " is external to this organization, " +
                        "and not allowed to be in this organization's groups");
            } else if (newMember.exists() && !newMember.getOrganization().equals(org)) {
                throw new ExWrongOrganization(newMember + " in wrong org");
            } else if (!newMember.exists()) {
                ImmutableCollection<Group> groups = newMember.getGroups();
                // check if the user is a pending member of a different organization's group
                if (!groups.isEmpty() && !groups.iterator().next().getOrganization().equals(org)) {
                    throw new ExWrongOrganization(newMember + " in wrong org");
                }
            }
            AffectedUserIDsAndInvitedFolders updates = group.addMember(newMember);
            needsACLUpdate.addAll(updates._affected);
            emails.add(_invitationHelper.createBatchFolderInvitationAndEmailer(group, admin,
                    newMember, updates._folders));
        }

        _aclPublisher.publish_(needsACLUpdate.build());

        _sqlTrans.commit();

        l.info("{} added {} member(s) to {}", admin, userEmails.size(), group);

        for (InvitationEmailer email : emails) {
            try {
                email.send();
            } catch (Exception e) {
                l.warn("failed to send email notifying {} of folder invitations from joining {}",
                        admin, group);
            }
        }

        return createReply(Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Void> removeGroupMembers(Integer groupID, List<String> userEmails)
            throws
            Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        // Permissions: cannot modify a group unless you are an admin.
        user.throwIfNotAdmin();
        Group group = _factGroup.create(groupID);
        // Permissions: cannot modify a group unless it is owned by your organization.
        throwIfNotOwner(user.getOrganization(), group);
        // Permissions: cannot modify externally managed groups.
        group.throwIfExternallyManaged();

        ImmutableSet.Builder<UserID> affected = ImmutableSet.builder();
        for (String userEmail : userEmails) {
            User u = _factUser.create(UserID.fromExternal(userEmail));
            affected.addAll(group.removeMember(u, null));
        }

        _aclPublisher.publish_(affected.build());

        _sqlTrans.commit();

        // TODO (RD) send removed from group email

        l.info("{} removed {} member(s) from {}", user, userEmails.size(), group);

        return createReply(Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Void> deleteGroup(Integer groupID) throws
            Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        // Permissions: cannot delete a group unless you are an admin.
        user.throwIfNotAdmin();
        Group group = _factGroup.create(groupID);
        // Permissions: cannot modify a group unless it is owned by your organization.
        throwIfNotOwner(user.getOrganization(), group);
        // Permissions: cannot modify externally managed groups.
        group.throwIfExternallyManaged();
        ImmutableCollection<UserID> affected = group.delete();

        _aclPublisher.publish_(affected);

        _sqlTrans.commit();

        l.info("{} deleted {}", user, group);

        return createReply(Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<ListGroupsReply> listGroups(Integer maxResults, Integer offset,
            String searchPrefix) throws
            SQLException,
            ExNotAuthenticated,
            ExSecondFactorRequired,
            ExNotFound,
            ExNoPerm,
            ExBadArgs,
            ExSecondFactorSetupRequired
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Organization org = user.getOrganization();
        int groupCount = org.countGroups();
        ListGroupsReply reply = ListGroupsReply.newBuilder()
                .addAllGroups(groups2pbGroups(org.listGroups(maxResults, offset, searchPrefix)))
                .setTotalCount(org.countGroups())
                .build();
        _sqlTrans.commit();

        l.info("{} listed groups; total: {}", user, reply.getGroupsCount());
        
        return createReply(reply);
    }

    private static List<PBGroup> groups2pbGroups(Collection<Group> groups)
            throws SQLException, ExNotFound
    {
        List<PBGroup> pb = Lists.newArrayListWithCapacity(groups.size());
        for (Group group : groups) {
            pb.add(PBGroup.newBuilder()
                    .setGroupId(group.id().getInt())
                    .setCommonName(group.getCommonName())
                    .setExternallyManaged(group.isExternallyManaged())
                    .build());
        }
        return pb;
    }

    @Override
    public ListenableFuture<ListGroupMembersReply> listGroupMembers(Integer groupID) throws
            SQLException,
            ExNotAuthenticated,
            ExSecondFactorRequired,
            ExNotFound,
            ExNoPerm,
            ExSecondFactorSetupRequired,
            ExBadArgs
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        Group group = _factGroup.create(groupID);
        // Permissions: cannot list group members unless your org owns the group in question.
        throwIfNotOwner(user.getOrganization(), group);
        ListGroupMembersReply.Builder reply = ListGroupMembersReply.newBuilder();
        for (User u : group.listMembers()) {
            reply.addUsers(user2pb(u, getUserFullNameOrEmpty(u)));
        }
        _sqlTrans.commit();

        l.info("{} listed members for {}; total: {}", user, group, reply.getUsersCount());

        return createReply(reply.build());
    }

    @Override
    public ListenableFuture<ListGroupStatusInSharedFolderReply> listGroupStatusInSharedFolder(
            Integer groupID, ByteString shareID)
            throws Exception
    {
        _sqlTrans.begin();
        Group group = _factGroup.create(groupID);
        // Auth check.
        _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY);
        // N.B. we don't check that the sessionOrg owns the group here because we want any member of
        // a shared folder to be able to list the members of the groups in that folder.
        SharedFolder sf = _factSharedFolder.create(shareID);

        ListGroupStatusInSharedFolderReply.Builder builder = ListGroupStatusInSharedFolderReply.newBuilder();
        for (UserPermissionsAndState ups : sf.getUserRolesAndStatesForGroup(group)) {
            builder.addUserAndState(PBUserAndState.newBuilder()
                    .setUser(user2pb(ups._user))
                    .setState(ups._state.toPB())
                    .build());
        }
        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> syncGroupsWithLdapEndpoint()
            throws Exception
    {
        _sqlTrans.begin();
        User user = _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.INTERACTIVE);
        user.throwIfNotAdmin();
        Organization sessionOrg = user.getOrganization();
        _sqlTrans.commit();

        if (LDAP_GROUP_SYNCING_ENABLED) {
            // commit transaction before calling this because the LDAP Group Synchronizer assumes
            // that the sqlTransaction starts off inactive
            syncGroupsWithLDAPImpl(sessionOrg);
        }
        return createVoidReply();
    }

    private void syncGroupsWithLDAPImpl(@Nullable Organization org)
            throws Exception
    {
        if (org == null) {
            // org is null when called automatically, since we can't grab a session org;
            // default to the private organization, since LDAP is only enabled in private cloud
            org = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
        }

        AffectedUsersAndError result = _ldapGroupSynchronizer.synchronizeGroups(_sqlTrans, org);
        _aclPublisher.publish_(result._affected);

        if (result._errored) {
            l.warn("ldap group syncing did not complete successfully, view log for details");
            // TODO (RD) set up an exponential retry?
        }
    }

    int getSeatCountForPrivateOrg() throws SQLException
    {
        _sqlTrans.begin();
        Organization privateOrg = _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
        int returnValue = privateOrg.countUsers();
        _sqlTrans.commit();

        return returnValue;
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
    public static final int ABSOLUTE_MAX_RESULTS = 1000;

    static private void throwOnInvalidMaxResults(int maxResults) throws ExBadArgs
    {
        if (maxResults > ABSOLUTE_MAX_RESULTS) throw new ExBadArgs("maxResults is too big");
        else if (maxResults < 0) throw new ExBadArgs("maxResults is a negative number");
    }

    private static void throwOnInvalidEmailAddress(String emailAddress)
            throws ExInvalidEmailAddress
    {
        if (!Util.isValidEmailAddress(emailAddress)) throw new ExInvalidEmailAddress();
    }
}
