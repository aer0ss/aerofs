package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.proto.Common.PBRole;
import com.aerofs.proto.Sp.AcceptOrganizationInvitationReply;
import com.aerofs.proto.Sp.DeleteOrganizationInvitationForUserReply;
import com.aerofs.proto.Sp.DeleteOrganizationInvitationReply;
import com.aerofs.proto.Sp.GetStripeDataReply;
import com.aerofs.proto.Sp.InviteToOrganizationReply;
import com.aerofs.proto.Sp.ListOrganizationInvitedUsersReply;
import com.aerofs.proto.Sp.ListOrganizationMembersReply;
import com.aerofs.proto.Sp.ListOrganizationMembersReply.PBUserAndLevel;
import com.aerofs.proto.Sp.ListOrganizationSharedFoldersReply;
import com.aerofs.proto.Sp.ListUserDevicesReply.PBDevice;
import com.aerofs.proto.Sp.ListUserSharedFoldersReply;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.Builder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserAndRole;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Sp.AckCommandQueueHeadReply;
import com.aerofs.proto.Sp.GetCommandQueueHeadReply;
import com.aerofs.proto.Sp.PBStripeData;
import com.aerofs.proto.Sv;
import com.aerofs.proto.Sp.RegisterDeviceReply;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueElement;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.SuccessError;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueSize;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Common.PBFolderInvitation;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.proto.Sp.ListUserDevicesReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.proto.Sp.ResolveSignUpCodeReply;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolder.Factory;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.SPDatabase.DeviceInfo;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetHeartInvitesQuotaReply;
import com.aerofs.proto.Sp.GetOrgPreferencesReply;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.proto.Sp.GetUnsubscribeEmailReply;
import com.aerofs.proto.Sp.GetUserCRLReply;
import com.aerofs.proto.Sp.ISPService;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.SpNotifications.PBACLNotification;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import sun.security.pkcs.PKCS10;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SPService implements ISPService
{
    private static final Logger l = Loggers.getLogger(SPService.class);

    // TODO (WW) remove dependency to these database objects
    private final SPDatabase _db;
    private final CertificateDatabase _certdb;
    private final EmailSubscriptionDatabase _esdb;

    private final SQLThreadLocalTransaction _sqlTrans;

    private VerkehrPublisher _verkehrPublisher;
    private VerkehrAdmin _verkehrAdmin;

    private SPActiveUserSessionTracker _userTracker;
    private SPSessionInvalidator _sessionInvalidator;
    private SPSessionExtender _sessionExtender;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.get() returns the userId associated with the current HttpSession.
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
    private final InvitationEmailer.Factory _factEmailer;

    private final JedisEpochCommandQueue _commandQueue;
    private final JedisThreadLocalTransaction _jedisTrans;

    // Remember to udpate text in team_members.mako, team_settings.mako, and pricing.mako when
    // changing this number.
    private int _maxFreeMembersPerTeam = 3;

    // Remember to udpate text in shared_foler.mako, shared_folder_modals.mako, team_settings.mako,
    // and pricing.mako when changing this number.
    private int _maxFreeCollaboratorsPerFolder = 1;

    SPService(SPDatabase db, SQLThreadLocalTransaction sqlTrans,
            JedisThreadLocalTransaction jedisTrans, ISessionUser sessionUser,
            PasswordManagement passwordManagement,
            CertificateAuthenticator certificateAuthenticator, User.Factory factUser,
            Organization.Factory factOrg, OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice, CertificateDatabase certdb,
            EmailSubscriptionDatabase esdb, Factory factSharedFolder,
            InvitationEmailer.Factory factEmailer, DeviceRegistrationEmailer deviceRegistrationEmailer,
            RequestToSignUpEmailer requestToSignUpEmailer, JedisEpochCommandQueue commandQueue)
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
        _factEmailer = factEmailer;

        _commandQueue = commandQueue;
    }

    /**
     * For testing only. Don't use in production.
     * TODO (WW) use configuration service.
     */
    public void setMaxFreeUserCounts(int maxFreeMembersPerTeam, int maxFreeCollaboratorsPerFolder)
    {
        _maxFreeMembersPerTeam = maxFreeMembersPerTeam;
        _maxFreeCollaboratorsPerFolder = maxFreeCollaboratorsPerFolder;
    }

    public void setVerkehrClients_(VerkehrPublisher verkehrPublisher, VerkehrAdmin verkehrAdmin)
    {
        assert verkehrPublisher != null;
        assert verkehrAdmin != null;

        _verkehrPublisher = verkehrPublisher;
        _verkehrAdmin = verkehrAdmin;
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
            user = _sessionUser.exists() ? _sessionUser.get().id().getString() : "user unknown";
        } catch (ExNoPerm enp) {
            throw SystemUtil.fatalWithReturn(enp);
        }

        l.warn(user + ": " + Util.e(e,
                ExNoPerm.class,
                ExBadCredential.class,
                ExBadArgs.class,
                ExAlreadyExist.class));

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
    public ListenableFuture<GetUserPreferencesReply> getUserPreferences(ByteString deviceId)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        FullName fn = user.getFullName();
        Device device = _factDevice.create(deviceId);

        GetUserPreferencesReply reply = GetUserPreferencesReply.newBuilder()
                .setFirstName(fn._first)
                .setLastName(fn._last)
                // Some early Alpha testers don't have their device information in the database.
                // An UPUT adds the devices back only if they relaunches.
                .setDeviceName(device.exists() ? device.getName() : "")
                .build();

        _sqlTrans.commit();

        return createReply(reply);
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
        User user = userID == null ? _sessionUser.get() : _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        if (firstName != null || lastName != null) {
            if (firstName == null || lastName == null) {
                throw new ExBadArgs("First and last name must both be non-null or both null");
            }

            FullName fullName = new FullName(firstName, lastName);
            l.info("{} set full name: {}, session user {}", user, fullName, _sessionUser.get());
            user.setName(fullName);
            userNameUpdated = true;
        }

        if (deviceId != null) {
            Device device = _factDevice.create(deviceId);
            throwIfNotOwner(user, device);

            // TODO (WW) print session user in log headers
            l.info("{} set device name: {}, session user {}", user, deviceName, _sessionUser.get());
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
    public ListenableFuture<ListOrganizationMembersReply> listOrganizationMembers(
            Integer maxResults, Integer offset)
            throws Exception
    {
        throwOnInvalidOffset(offset);
        throwOnInvalidMaxResults(maxResults);

        _sqlTrans.begin();

        User user = _sessionUser.get();
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

        User user = _sessionUser.get();
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

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        ListOrganizationInvitedUsersReply.Builder builder =
                ListOrganizationInvitedUsersReply.newBuilder();
        for (OrganizationInvitation oi : user.getOrganization().getOrganizationInvitations()) {
            builder.addUserId(oi.getInvitee().id().getString());
        }

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<ListUserSharedFoldersReply> listUserSharedFolders(String userID)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        List<PBSharedFolder> pbs = sharedFolders2pb(user.getSharedFolders(),
                _sessionUser.get().getOrganization());

        _sqlTrans.commit();

        return createReply(ListUserSharedFoldersReply.newBuilder().addAllSharedFolder(pbs).build());
    }

    @Override
    public ListenableFuture<ListUserDevicesReply> listUserDevices(String userID)
            throws ExNoPerm, SQLException, ExFormatError, ExNotFound, ExEmptyEmailAddress
    {
        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userID);
        throwIfSessionUserIsNotOrAdminOf(user);

        ListUserDevicesReply.Builder builder = ListUserDevicesReply.newBuilder();
        for (Device device : user.getDevices()) {
            builder.addDevice(PBDevice.newBuilder()
                    .setDeviceId(ByteString.copyFrom(device.id().getBytes()))
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
            throws ExNoPerm, SQLException, ExNotFound
    {
        User currentUser = _sessionUser.get();

        if (user.equals(currentUser)) return;

        // if the current user is different from the specified user, the current user must be
        // an admin of the organization the specified user belongs to.
        currentUser.throwIfNotAdmin();

        // TODO (WW) use this string for all ExNoPerm's?
        String noPermMsg = "you don't have permission to perform this action";

        // Throw early if the specified user doesn't exist rather than relying on the following
        // below. This is to prevent attacker from testing user existance.
        if (!user.exists()) throw new ExNoPerm(noPermMsg);

        if (!user.getOrganization().equals(currentUser.getOrganization())) {
            throw new ExNoPerm(noPermMsg);
        }
    }

    /**
     * @param sessionOrg the organization of the session user
     */
    private List<PBSharedFolder> sharedFolders2pb(Collection<SharedFolder> sfs,
            Organization sessionOrg)
            throws ExNoPerm, ExNotFound, SQLException
    {
        // A cache to avoid excessive database queries. This should be obsolete with memcached.
        Map<User, FullName> user2nameCache = Maps.newHashMap();

        List<PBSharedFolder> pbs = Lists.newArrayListWithCapacity(sfs.size());
        for (SharedFolder sf : sfs) {

            // skip root stores. N.B. Organization.listSharedFolders never return root stores
            if (sf.id().isRoot()) continue;

            PBSharedFolder.Builder builder = PBSharedFolder.newBuilder()
                    .setStoreId(sf.id().toPB())
                    .setName(sf.getName())
                    .setOwnedByTeam(false);

            // fill in folder members
            for (SubjectRolePair srp : sf.getMemberACL()) {
                sharedFolderMember2pb(sessionOrg, user2nameCache, builder, srp);
            }

            pbs.add(builder.build());
        }
        return pbs;
    }

    private void sharedFolderMember2pb(Organization sessionOrg,
            Map<User, FullName> user2nameCache, Builder builder, SubjectRolePair srp)
            throws ExNotFound, SQLException
    {
        // don't add team server to the list.
        if (srp._subject.isTeamServerID()) return;

        User subject = _factUser.create(srp._subject);

        // the folder is owned by the session organization if an owner of the folder belongs to
        // the org.
        if (srp._role.covers(Role.OWNER) && subject.getOrganization().equals(sessionOrg)) {
            builder.setOwnedByTeam(true);
        }

        // retrieve the full name
        FullName fn = user2nameCache.get(subject);
        if (fn == null) {
            fn = subject.getFullName();
            user2nameCache.put(subject, fn);
        }

        builder.addUserAndRole(PBUserAndRole.newBuilder()
                .setRole(srp._role.toPB())
                .setUser(user2pb(subject, fn)));
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

        User requester = _sessionUser.get();
        User subject = _factUser.createFromExternalID(userIdString);
        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        l.info("Set auth requester=" + requester.id() + " subject=" + subject.id() + " auth=" +
                newAuth);

        // Verify caller and subject's organization match
        if (!subject.exists() || !requester.getOrganization().equals(subject.getOrganization())) {
            throw new ExNoPerm(subject.id().getString() + " is not a member of your team. " +
                    "Please invite the user to your team first.");
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

        User user = _sessionUser.get();

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

        final Organization org = _sessionUser.get().getOrganization();

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

        User user = _sessionUser.get();
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

        AuthorizationLevel level = _sessionUser.get().getLevel();

        _sqlTrans.commit();

        return createReply(GetAuthorizationLevelReply.newBuilder().setLevel(level.toPB()).build());
    }

    @Override
    public ListenableFuture<GetTeamServerUserIDReply> getTeamServerUserID()
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();

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
            Boolean recertifyDoNotUse, String osFamily, String osName, String deviceName)
            throws Exception
    {
        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove nulltoEmpty calls
        osFamily = Strings.nullToEmpty(osFamily);
        osName = Strings.nullToEmpty(osName);
        deviceName = Strings.nullToEmpty(deviceName);

        User user = _sessionUser.get();
        Device device = _factDevice.create(deviceId);

        CertificationResult cert = device.certify(new PKCS10(csr.toByteArray()), user);

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
        User user = _sessionUser.get();
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
        User user = _sessionUser.get();
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
        User user = _sessionUser.get();
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
            ByteString csr, Boolean recertifyDoNotUse, String osFamily, String osName,
            String deviceName)
            throws Exception
    {
        // WAIT_FOR_SP_PROTOCOL_VERSION_CHANGE remove nulltoEmpty calls
        osFamily = Strings.nullToEmpty(osFamily);
        osName = Strings.nullToEmpty(osName);
        deviceName = Strings.nullToEmpty(deviceName);

        User user = _sessionUser.get();

        // We need two transactions. The first is read only, so no rollback ability needed. In
        // between the transaction we make an RPC call.
        _sqlTrans.begin();

        user.throwIfNotAdmin();
        User tsUser = user.getOrganization().getTeamServerUser();

        _sqlTrans.commit();

        // This should not be part of a transaction because it involves an RPC call
        // FIXME: (PH) This leaves us vulnerable to a situation where the users organization changes
        // between the first transaction and the second transaction. This would result in a no-sync
        // on the team server.
        Device device = _factDevice.create(deviceId);
        CertificationResult cert = device.certify(new PKCS10(csr.toByteArray()), tsUser);

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

    @Override
    public ListenableFuture<GetSharedFolderNamesReply> getSharedFolderNames(
            List<ByteString> shareIds)
            throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        List<String> names = Lists.newArrayListWithCapacity(shareIds.size());
        for (ByteString shareId : shareIds) {
            SharedFolder sf = _factSharedFolder.create(shareId);
            // throws ExNoPerm if the user doesn't have permission to view the name
            sf.getMemberRoleThrows(user);
            names.add(sf.getName());
        }

        _sqlTrans.commit();

        return createReply(GetSharedFolderNamesReply.newBuilder().addAllFolderName(names).build());
    }

    @Override
    public ListenableFuture<Void> emailUser(String userId, String body)
            throws Exception
    {
        _sqlTrans.begin();

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME,
                _sessionUser.get().id().getString(), null, UserID.fromExternal(userId).getString(),
                body, null, true, null);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetHeartInvitesQuotaReply> noop()
            throws Exception
    {
        _sqlTrans.begin();

        GetHeartInvitesQuotaReply reply = GetHeartInvitesQuotaReply.newBuilder()
                .setCount(10)
                .build();

        _sqlTrans.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<PBSubjectRolePair> rolePairs, @Nullable String note)
            throws Exception
    {
        SharedFolder sf = _factSharedFolder.create(shareId);
        if (sf.id().isRoot()) throw new ExBadArgs("Cannot share root");

        User sharer = _sessionUser.get();
        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(rolePairs);

        l.info(sharer + " shares " + sf + " with " + srps);
        if (srps.isEmpty()) throw new ExBadArgs("must specify one or more sharee");

        _sqlTrans.begin();

        Collection<UserID> users = saveSharedFolderIfNecessary(folderName, sf, sharer);

        List<InvitationEmailer> emailers = createFolderInvitationAndEmailer(folderName, note, sf,
                sharer, srps);

        throwIfPaymentRequiredAndNoCustomerID(sharer.getOrganization(), sf, srps2users(srps));

        // send verkehr notification as the last step of the transaction
        publishACLs_(users);

        _sqlTrans.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    ImmutableCollection<User> srps2users(Collection<SubjectRolePair> srps)
    {
        ImmutableCollection.Builder<User> builder = ImmutableList.builder();
        for (SubjectRolePair srp : srps) builder.add(_factUser.create(srp._subject));
        return builder.build();
    }

    private Collection<UserID> saveSharedFolderIfNecessary(String folderName, SharedFolder sf,
            User sharer)
            throws ExNotFound, SQLException, ExNoPerm, IOException, ExAlreadyExist
    {
        if (sf.exists()) {
            sf.throwIfNoPrivilegeToChangeACL(sharer);
            return Collections.emptySet();
        }

        // The store doesn't exist. Create it and add the user as the owner.
        return sf.save(folderName, sharer);
    }

    private List<InvitationEmailer> createFolderInvitationAndEmailer(String folderName, String note,
            SharedFolder sf, User sharer, List<SubjectRolePair> srps)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExBadArgs
    {
        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        // create invitations
        for (SubjectRolePair srp : srps) {
            User sharee = _factUser.create(srp._subject);
            if (sharee.equals(sharer)) {
                // TODO (WW) change to: throw new ExBadArgs("are you trying to invite yourself?");
                l.warn(sharer + " tried to invite himself");
                continue;
            }

            emailers.add(createFolderInvitationAndEmailer(sf, sharer, sharee, srp._role, note,
                    folderName));
        }

        return emailers;
    }

    private InvitationEmailer createFolderInvitationAndEmailer(SharedFolder sf, User sharer,
            User sharee, Role role, String note, String folderName)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist
    {
        if (sf.isMember(sharee)) throw new ExAlreadyExist(sharee.id() + " is already a member");

        // Add a pending ACL entry if the user doesn't exist in the ACL
        if (!sf.isPending(sharee)) sf.addPendingACL(sharer, sharee, role);

        InvitationEmailer emailer;
        if (sharee.exists()) {
            // send folder invitation email
            emailer = _factEmailer.createFolderInvitationEmailer(sharer, sharee, folderName, note, sf.id());
        } else {
            emailer = inviteToSignUp(sharer, sharee, folderName, note)._emailer;
        }
        return emailer;
    }

    static class InviteToSignUpResult
    {
        final InvitationEmailer _emailer;
        final String _signUpCode;

        InviteToSignUpResult(InvitationEmailer emailer, String signUpCode)
        {
            _emailer = emailer;
            _signUpCode = signUpCode;
        }
    }

    /**
     * Call this method to use an inviter name different from inviter.getFullName()._first
     */
    InviteToSignUpResult inviteToSignUp(User inviter, User invitee, @Nullable String folderName,
            @Nullable String note)
            throws SQLException, IOException, ExNotFound
    {
        assert !invitee.exists();

        String code = invitee.addSignUpCode();

        _esdb.insertEmailSubscription(invitee.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        InvitationEmailer emailer = _factEmailer.createSignUpInvitationEmailer(inviter, invitee,
                folderName, note, code);

        return new InviteToSignUpResult(emailer, code);
    }

    @Override
    public ListenableFuture<Void> joinSharedFolder(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " joins " + sf);

        if (!sf.exists()) throw new ExNotFound("No such shared folder");

        if (sf.isMember(user)) {
            throw new ExAlreadyExist("You are already a member of this shared folder");
        }

        if (!sf.isPending(user)) {
            throw new ExNoPerm("Your have not been invited to this shared folder");
        }

        // reset pending bit to make user a member of the shared folder
        Collection<UserID> users = sf.setMember(user);

        Collection<Device> peerDevices = user.getPeerDevices();
        // Refresh CRLs for peer devices once this user joins the shared folder (since the peer user
        // map may have changed).
        for (Device peer : peerDevices) {
            l.info(peer.id().toStringFormal() + ": crl refresh");
            addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.REFRESH_CRL);
        }

        // always call this method as the last step of the transaction
        publishACLs_(users);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> ignoreSharedFolderInvitation(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " ignore " + sf);

        if (!sf.exists()) {
            throw new ExNotFound("No such shared folder");
        }
        if (sf.isMember(user)) {
            throw new ExAlreadyExist("You have already accepted this invitation");
        }
        if (!sf.isPending(user)) {
            throw new ExNoPerm("You have not been invited to this shared folder");
        }

        // Ignore the invitation by deleting the ACL.
        sf.deleteMemberOrPendingACL(user);

        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> leaveSharedFolder(ByteString sid) throws Exception
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " leave " + sf);

        if (!sf.exists()) throw new ExNotFound("No such shared folder");

        if (sf.id().isRoot()) throw new ExBadArgs("Cannot leave root folder");

        // silently ignore leave call from pending users as multiple device of the same user
        // may make the call depending on the relative speeds of deletion propagation vs ACL
        // propagation
        if (!sf.isPending(user)) {
            if (!sf.isMember(user)) {
                throw new ExNotFound("You are not a member of this shared folder");
            }

            // set pending bit
            Collection<UserID> users = sf.setPending(user);

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

        User user = _sessionUser.get();

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
    public ListenableFuture<Void> sendEmailVerification()
    {
        throw new NotImplementedException();
    }

    @Override
    public ListenableFuture<Void> verifyEmail(String verificationCode)
    {
        throw new NotImplementedException();
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
    public ListenableFuture<Void> requestToSignUpWithBusinessPlan(String emailAddress)
            throws Exception
    {
        User user = _factUser.createFromExternalID(emailAddress);

        _sqlTrans.begin();

        @Nullable String signUpCode;
        if (user.exists()) {
            signUpCode = null;
            // no-op instead of throwing to avoid leaking email information to attackers
        } else {
            signUpCode = user.addSignUpCode();
            // Retrieve the email address from the user id in case the original address is not
            // normalized.
            emailAddress = user.id().getString();
        }

        _sqlTrans.commit();

        // Send the email out of the transaction
        if (signUpCode == null) {
            _requestToSignUpEmailer.sendRequestToActivateBusinessPlan(emailAddress);
        } else {
            _requestToSignUpEmailer.sendRequestToSignUpAndActivateBusinessPlan(emailAddress,
                    signUpCode);
        }

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> inviteToSignUp(List<String> userIdStrings)
            throws SQLException, ExBadArgs, ExEmailSendingFailed, ExNotFound, IOException, ExNoPerm,
            ExEmptyEmailAddress
    {
        if (userIdStrings.isEmpty()) {
            throw new ExBadArgs("Must specify one or more invitees");
        }

        _sqlTrans.begin();

        User inviter = _sessionUser.get();
        l.info("invite " + userIdStrings.size() + " users by " + inviter);

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();
        for (String inviteeString : userIdStrings) {
            User invitee = _factUser.createFromExternalID(inviteeString);

            if (invitee.exists()) {
                l.info(inviter + " invites " + invitee + ": already exists. skip.");
            } else {
                emailers.add(inviteToSignUp(inviter, invitee, null, null)._emailer);
            }
        }

        _sqlTrans.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<InviteToOrganizationReply> inviteToOrganization(String userIdString)
            throws SQLException, ExNoPerm, ExNotFound, IOException, ExEmailSendingFailed,
            ExAlreadyExist, ExAlreadyInvited, ExNoStripeCustomerID, ExEmptyEmailAddress
    {
        _sqlTrans.begin();

        User inviter = _sessionUser.get();
        User invitee = _factUser.create(UserID.fromExternal(userIdString));
        Organization org = inviter.getOrganization();

        l.info("{} sends team invite to {}", inviter, invitee);

        inviter.throwIfNotAdmin();

        InvitationEmailer emailer;
        if (!invitee.exists()) {
            // The user doesn't exist. Send him a sign-up invitation email only, and associate the
            // signup code with the team invitation. See signUpWithCode() on how this association is
            // consumed.
            InviteToSignUpResult res = inviteToSignUp(inviter, invitee, null, null);
            inviteToTeam(inviter, invitee, org, res._signUpCode);
            emailer = res._emailer;
        } else {
            // The user exists. Send him a team invitation email.
            if (invitee.getOrganization().equals(org)) {
                throw new ExAlreadyExist(invitee + " is already a member of the team");
            }
            emailer = inviteToTeam(inviter, invitee, org, null);
        }

        PBStripeData sd = getStripeData(org);
        throwIfPaymentRequiredAndNoCustomerID(sd);

        _sqlTrans.commit();

        // send the email after transaction since it may take some time and it's okay to fail
        emailer.send();

        return createReply(InviteToOrganizationReply.newBuilder().setStripeData(sd).build());
    }

    /**
     * Note that the invitee may not exist when the method is called.
     *
     * @param signUpCode The signUp code to be associated with the team invitation. As soon as the
     *  user signs up with the associated code, the system will automatically accept the team
     *  invitation. See signUpWithCode().
     */
    InvitationEmailer inviteToTeam(User inviter, User invitee, Organization org,
            @Nullable String signUpCode)
            throws ExAlreadyExist, SQLException, ExNotFound, ExAlreadyInvited, IOException
    {
        OrganizationInvitation invite = _factOrgInvite.create(invitee, org);

        if (invite.exists()) throw new ExAlreadyInvited();

        _factOrgInvite.save(inviter, invitee, org, signUpCode);

        return _factEmailer.createOrganizationInvitationEmailer(inviter, invitee, org);
    }

    @Override
    public ListenableFuture<AcceptOrganizationInvitationReply> acceptOrganizationInvitation(Integer orgID)
            throws Exception
    {
        _sqlTrans.begin();

        User accepter = _sessionUser.get();
        Organization orgOld = accepter.getOrganization();
        Organization orgNew = _factOrg.create(orgID);

        Collection<UserID> users = acceptTeamInvitation(accepter, orgNew);

        // retrieve the stripe data for the old team _after_ the user moves out.
        PBStripeData sd = getStripeData(orgOld);

        publishACLs_(users);

        _sqlTrans.commit();

        return createReply(AcceptOrganizationInvitationReply.newBuilder().setStripeData(sd).build());
    }

    /**
     * @return a collection of users to be passed into publishACLs_()
     */
    private Collection<UserID> acceptTeamInvitation(User accepter, Organization org)
            throws Exception
    {
        l.info("{} accept org invite to {}", accepter, org);

        OrganizationInvitation invite = _factOrgInvite.create(accepter, org);

        // Check to see if the user was actually invited to this organization.
        if (!invite.exists()) throw new ExNotFound();

        if (accepter.getOrganization().equals(org)) {
            throw new ExBadArgs(accepter + " is already part of the team");
        }

        invite.delete();

        return accepter.setOrganization(invite.getOrganization(), AuthorizationLevel.USER);
    }

    public void migrateDefaultOrgUsers()
            throws Exception
    {
        while (true) {
            l.warn("======================= next batch");

            _sqlTrans.begin();

            Collection<UserID> users = Lists.newArrayList();
            boolean done = true;

            ResultSet rs = _factUser.getDefaultOrgUsers();
            try {
                int count = 0;
                while (rs.next()) {
                    User user = _factUser.create(UserID.fromInternal(rs.getString(1)));
                    l.warn(">>>>>> migrate " + user);
                    Organization org = _factOrg.save();
                    users.addAll(user.setOrganization(org, AuthorizationLevel.ADMIN));
                    if (count++ > 50) {
                        done = false;
                        break;
                    }
                }
            } finally {
                rs.close();
            }

            publishACLs_(users);

            _sqlTrans.commit();

            if (done) break;
        }

    }

    @Override
    public ListenableFuture<DeleteOrganizationInvitationReply> deleteOrganizationInvitation(
            Integer orgID)
            throws SQLException, ExNoPerm, ExNotFound
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        Organization org = _factOrg.create(orgID);
        l.info("Delete org invite by " + user);

        _factOrgInvite.create(user, org).delete();

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        return createReply(
                DeleteOrganizationInvitationReply.newBuilder().setStripeData(sd).build());
    }

    @Override
    public ListenableFuture<DeleteOrganizationInvitationForUserReply> deleteOrganizationInvitationForUser(
            String userID)
            throws SQLException, ExNoPerm, ExNotFound, ExEmptyEmailAddress
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();

        _factOrgInvite.create(_factUser.createFromExternalID(userID), org).delete();

        PBStripeData sd = getStripeData(org);

        _sqlTrans.commit();

        return createReply(
                DeleteOrganizationInvitationForUserReply.newBuilder().setStripeData(sd).build());
    }

    /**
     * Return a StripeData object for the specified org based on its team size and # collaborators.
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
        if (sd.getQuantity() > _maxFreeMembersPerTeam && !sd.hasCustomerId()) {
            throw new ExNoStripeCustomerID();
        }
    }

    /**
     * Throw if the shared folder exceeds maximum # collaborators, there are collaborators in the
     * invitees list, and the org doesn't have a Stripe
     * customer ID.
     */
    private void throwIfPaymentRequiredAndNoCustomerID(Organization org, SharedFolder sf,
            Collection<User> invitees)
            throws ExNoStripeCustomerID, SQLException, ExNotFound
    {
        // Okay if the customer is paying
        if (org.getStripeCustomerIDNullable() != null) return;

        // Okay if no collaborators are invited
        boolean collaboratorInvited = false;
        for (User invitee : invitees) {
            if (isCollaborator(org, invitee)) {
                collaboratorInvited = true;
                break;
            }
        }
        if (!collaboratorInvited) return;

        int collaborators = 0;
        for (User user : sf.getAllUsers()) {
            if (isCollaborator(org, user) && ++collaborators > _maxFreeCollaboratorsPerFolder) {
                throw new ExNoStripeCustomerID();
            }
        }
    }

    /**
     * Return whether the user is an external collaborator of the org
     */
    private boolean isCollaborator(Organization org, User user)
            throws SQLException, ExNotFound
    {
        // !user.exists() is needed for users who are pending user of a shared folder and haven't
        // signed up yet.
        return !user.id().isTeamServerID() &&
                (!user.exists() || !user.getOrganization().equals(org));
    }

    @Override
    public ListenableFuture<GetStripeDataReply> getStripeData()
            throws SQLException, ExNoPerm, ExNotFound
    {
        _sqlTrans.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        GetStripeDataReply.Builder builder = GetStripeDataReply.newBuilder()
                .setStripeData(getStripeData(user.getOrganization()));

        _sqlTrans.commit();

        return createReply(builder.build());
    }

    @Override
    public ListenableFuture<Void> noop3()
    {
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm
    {
        User user = _sessionUser.get();
        GetACLReply.Builder bd = GetACLReply.newBuilder();

        _sqlTrans.begin();

        long userEpoch = user.getACLEpoch();
        if (userEpoch == epoch) {
            l.info("no updates - matching epoch:" + epoch);
        } else {
            for (SharedFolder sf : user.getSharedFolders()) {
                l.info("add s:" + sf.id());
                PBStoreACL.Builder aclBuilder = PBStoreACL.newBuilder();
                aclBuilder.setStoreId(sf.id().toPB());
                for (SubjectRolePair srp : sf.getMemberACL()) {
                    l.info("add j:" + srp._subject + " r:" + srp._role.getDescription());
                    aclBuilder.addSubjectRole(srp.toPB());
                }
                bd.addStoreAcl(aclBuilder);
            }
        }
        _sqlTrans.commit();

        bd.setEpoch(userEpoch);
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<Void> updateACLDeprecated(ByteString storeId,
            List<PBSubjectRolePair> subjectRole)
            throws Exception
    {
        for (PBSubjectRolePair srp : subjectRole) {
            updateACL(storeId, srp.getSubject(), srp.getRole());
        }
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACLDEprecated(ByteString storeId, List<String> subjectList)
            throws Exception
    {
        for (String subject: subjectList) deleteACL(storeId, subject);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> updateACL(final ByteString storeId, String subjectString,
            PBRole role)
            throws Exception
    {
        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(storeId);

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        _sqlTrans.begin();
        sf.throwIfNoPrivilegeToChangeACL(user);
        User subject = _factUser.createFromExternalID(subjectString);
        // always call this method as the last step of the transaction
        publishACLs_(sf.updateMemberACL(subject, Role.fromPB(role)));
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId, String subjectString)
            throws Exception
    {
        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(storeId);

        User subject = _factUser.createFromExternalID(subjectString);

        _sqlTrans.begin();
        sf.throwIfNoPrivilegeToChangeACL(user);
        // always call this method as the last step of the transaction
        publishACLs_(sf.deleteMemberOrPendingACL(subject));
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
            String aclTopic = Param.ACL_CHANNEL_TOPIC_PREFIX + entry.getKey().getString();
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
    public ListenableFuture<Void> signIn(String userIdString, ByteString credentials)
            throws IOException, SQLException, ExBadCredential, ExNotFound, ExBadArgs,
            ExEmptyEmailAddress
    {
        _sqlTrans.begin();

        User user = _factUser.createFromExternalID(userIdString);

        if (user.id().isTeamServerID()) {
            // Team servers use certificates (in this case the passed credentials represent the
            // device ID).
            Device device;
            try {
                device = _factDevice.create(DID.fromExternal(credentials.toByteArray()));
            } catch (ExFormatError e) {
                l.error(user + ": did malformed");
                throw new ExBadCredential();
            }

            user.signInWithCertificate(_certauth, device);
        } else {
            // Regular users still use username/password credentials.
            user.signIn(SPParam.getShaedSP(credentials.toByteArray()));
        }

        // Set the session cookie.
        _sessionUser.set(user);
        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

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
        _passwordManagement.changePassword(_sessionUser.get().id(), old_credentials,
                new_credentials);
        _sqlTrans.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signUpWithCode(String signUpCode, ByteString credentials,
            String firstName, String lastName)
            throws Exception
    {
        // Sanitize names
        firstName = firstName.trim();
        lastName = lastName.trim();
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new ExBadArgs("First and last names must not be empty");
        }
        FullName fullName = new FullName(firstName, lastName);

        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());

        _sqlTrans.begin();

        UserID userID = _db.getSignUpCode(signUpCode);
        User user = _factUser.create(userID);
        Collection<UserID> users;
        if (user.exists()) {
            // If the user already exists and the password matches the existing password, we do an
            // no-op. This is needed for the business users to retry signing up using the link in
            // their email. That link points to the user signup page with business signup as the
            // followup page.
            if (!user.isCredentialCorrect(shaedSP)) {
                throw new ExBadCredential("Password doesn't match the existing account");
            }
            users = Collections.emptyList();
        } else {
            users = signUpWithCodeImpl(signUpCode, user, fullName, shaedSP);
        }

        // always call this method as the last step of the transaction
        publishACLs_(users);

        _sqlTrans.commit();

        SVClient.sendEventAsync(Sv.PBSVEvent.Type.SIGN_UP, "id: " + user.id().getString());

        return createVoidReply();
    }

    /**
     * @return a collection of users to be passed to publishACLs_()
     */
    private Collection<UserID> signUpWithCodeImpl(String signUpCode, User user, FullName fullName,
            byte[] shaedSP)
            throws Exception
    {
        l.info("sign up {} with code {}", user, signUpCode);

        user.save(shaedSP, fullName);

        // Unsubscribe user from the aerofs invitation reminder mailing list
        _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        // N.B. do not remove the sign up invitation code so we can support the case in the
        // above "if user.exist()" branch.

        // Accept team invitation if there is one associated with the signup code.
        OrganizationInvitation oi = _factOrgInvite.getBySignUpCodeNullable(signUpCode);
        if (oi == null) return Collections.emptyList();

        l.info("{} automatically accepts team invitation to {}", user, oi.getOrganization());

        if (!oi.getInvitee().equals(user)) {
            l.error("the org invite ({} => {} to {}) associated with the signup code {} " +
                    "doesn't match the signup user {}. how possible?", oi.getInviter(),
                    oi.getInvitee(), oi.getOrganization(), signUpCode, user);
            throw new ExNotFound();
        }

        return acceptTeamInvitation(user, oi.getOrganization());
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

        User user = _sessionUser.get();
        Device device = _factDevice.create(deviceId);
        throwIfSessionUserIsNotOrAdminOf(device.getOwner());

        // TODO (WW) print session user in log headers
        l.info("{} unlinks {}, erase {}, session user {}", user, device.id().toStringFormal(),
                erase, _sessionUser.get());

        unlinkDeviceImplementation(device, erase);

        _sqlTrans.commit();

        return createVoidReply();
    }

    /**
     * Perform certificate revocation and update the persistent command queue. Should be called with
     * a SQL transaction.
     * @param device the device to unlink.
     */
    private void unlinkDeviceImplementation(Device device, boolean erase)
            throws Exception
    {
        User owner = device.getOwner();
        device.delete();

        Certificate cert = device.certificate();

        // Do not try to revoke certs that do not exist. This will only effect devices created
        // before the certificate tracking code was rolled out.
        boolean certExists = cert.exists();
        if (certExists) {
            cert.revoke();

            ImmutableList.Builder<Long> builder = ImmutableList.builder();
            builder.add(cert.serial());
            updateVerkehrCRL(builder.build());
        } else {
            l.warn(device.id() + " no cert exists. unlink anyway.");
        }

        Collection<Device> peerDevices = owner.getPeerDevices();

        // Tell peer devices to clean their sss database and refresh their certificate revocation
        // list.
        for (Device peer : peerDevices) {
            // Only need to refresh the CRL if we actually deleted a cert.
            if (certExists) {
                addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.REFRESH_CRL);
            }

            // Clean the sync status database regardless, since we deleted a device.
            addToCommandQueueAndSendVerkehrMessage(peer.id(), CommandType.CLEAN_SSS_DATABASE);
        }

        // Tell the actual device to perform the required local actions. Remember to flush the queue
        // first so that this is the only command left in the queue. The ensures that if the user
        // changes their password the unlink/wipe command can still be executed.
        if (erase) {
            addToCommandQueueAndSendVerkehrMessage(device.id(),
                    CommandType.UNLINK_AND_WIPE_SELF, true);
        } else {
            addToCommandQueueAndSendVerkehrMessage(device.id(),
                    CommandType.UNLINK_SELF, true);
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
        device.throwIfNotOwner(_sessionUser.get());
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

        Set<UserID> sharedUsers = _db.getSharedUsersSet(_sessionUser.get().id());

        GetDeviceInfoReply.Builder builder = GetDeviceInfoReply.newBuilder();
        for (ByteString did : dids) {
            DeviceInfo info = _db.getDeviceInfo(new DID(did));

            // If there is a permission error or the device does not exist, simply provide an empty
            // device info object.
            if (info != null && sharedUsers.contains(info._ownerID)) {
                builder.addDeviceInfo(GetDeviceInfoReply.PBDeviceInfo.newBuilder()
                    .setDeviceName(info._deviceName)
                    .setOwner(PBUser.newBuilder()
                        .setUserEmail(info._ownerID.getString())
                        .setFirstName(info._ownerFirstName)
                        .setLastName(info._ownerLastName)));
            } else {
                builder.addDeviceInfo(EMPTY_DEVICE_INFO);
            }
        }

        _sqlTrans.commit();

        return createReply(builder.build());
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

    private void updateVerkehrCRL(ImmutableList<Long> serials)
            throws Exception
    {
        l.info("command verkehr, #serials: " + serials.size());
        ListenableFuture<Void> succeeded = _verkehrAdmin.updateCRL(serials);

        verkehrFutureGet_(succeeded);
    }

    @Override
    public ListenableFuture<Void> noop4()
            throws Exception
    {
        return null;
    }

    @Override
    public ListenableFuture<Void> noop5()
            throws Exception
    {
        return null;
    }

    @Override
    public ListenableFuture<Void> noop6()
            throws Exception
    {
        return null;
    }

    @Override
    public ListenableFuture<Void> noop7()
            throws Exception
    {
        return null;
    }

}
