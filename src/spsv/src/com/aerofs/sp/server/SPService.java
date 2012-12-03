package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.S;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.OrganizationDatabase.UserInfo;
import com.aerofs.sp.server.lib.SPDatabase.DeviceInfo;
import com.aerofs.sp.server.lib.SPDatabase.ResolveTargetedSignUpCodeResult;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization.UserListAndQueryCount;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.CertifyDeviceReply;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.proto.Sp.GetHeartInvitesQuotaReply;
import com.aerofs.proto.Sp.GetOrgPreferencesReply;
import com.aerofs.proto.Sp.GetPreferencesReply;
import com.aerofs.proto.Sp.GetUnsubscribeEmailReply;
import com.aerofs.proto.Sp.GetUserCRLReply;
import com.aerofs.proto.Sp.ISPService;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import com.aerofs.proto.Sp.ListSharedFoldersResponse;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.proto.Sp.ListUsersReply;
import com.aerofs.proto.Sp.PBACLNotification;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.proto.Sp.ResolveSharedFolderCodeReply;
import com.aerofs.proto.Sp.ResolveTargetedSignUpCodeReply;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.sp.server.lib.ACLReturn;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPDatabase.FolderInvitation;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import sun.security.pkcs.PKCS10;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.aerofs.sp.server.SPParam.SP_EMAIL_NAME;

/** TODO (WW):
 *
 * ******************************************************************
 *  This class should contain NO business logic!
 *  First, please do not add more business logic here.
 *  Second, let's slowly toss out existing logic to other classes.
 * ******************************************************************
 */

class SPService implements ISPService
{
    private static final Logger l = Util.l(SPService.class);

    // the temporary user or device name used before SetPreferences is called
    private static final String UNKNOWN_NAME = "(unknown)";

    private final SPDatabase _db;
    private final CertificateDatabase _certdb;
    private final IThreadLocalTransaction<SQLException> _transaction;

    private VerkehrPublisher _verkehrPublisher;
    private VerkehrAdmin _verkehrAdmin;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.get() returns the userId associated with the current HttpSession.
    // Note that the session is set externally in SPServlet.
    private final ISessionUser _sessionUser;

    private final UserManagement _userManagement;
    private final OrganizationManagement _organizationManagement;
    private final SharedFolderManagement _sharedFolderManagement;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final Device.Factory _factDevice;

    SPService(SPDatabase db, IThreadLocalTransaction<SQLException> transaction,
            ISessionUser sessionUser, UserManagement userManagement,
            OrganizationManagement organizationManagement,
            SharedFolderManagement sharedFolderManagement,
            User.Factory factUser, Organization.Factory factOrg, Device.Factory factDevice,
            CertificateDatabase certdb)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _certdb = certdb;
        _transaction = transaction;
        _sessionUser = sessionUser;
        _userManagement = userManagement;
        _organizationManagement = organizationManagement;
        _sharedFolderManagement = sharedFolderManagement;
        _factUser = factUser;
        _factOrg = factOrg;
        _factDevice = factDevice;
    }

    public void setVerkehrClients_(VerkehrPublisher verkehrPublisher, VerkehrAdmin verkehrAdmin)
    {
        assert verkehrPublisher != null : ("cannot set null verkehr publisher client");
        assert verkehrAdmin != null : ("cannot set null verkehr admin client");

        _verkehrPublisher = verkehrPublisher;
        _verkehrAdmin = verkehrAdmin;
    }

    @Override
    public PBException encodeError(Throwable e)
    {
        // Report error in logs (if it is not an exception that we would normally expect).
        if (!(e instanceof ExNoPerm) &&
                !(e instanceof ExBadCredential) &&
                !(e instanceof ExBadArgs) &&
                !(e instanceof ExNotFound)) {
            l.warn("user: " + _sessionUser + ": " + Util.e(e));
        }

        // Notify SPTransaction that an exception occurred.
        _transaction.handleException();

        // don't include stack trace here to avoid expose SP internals to the client side.
        return Exceptions.toPB(e);
    }

    @Override
    public ListenableFuture<GetPreferencesReply> getPreferences(ByteString deviceId)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        FullName fn = user.getFullName();
        Device device = _factDevice.create(deviceId);

        GetPreferencesReply reply = GetPreferencesReply.newBuilder()
                .setFirstName(fn._first)
                .setLastName(fn._last)
                .setDeviceName(device.exists() ? device.getName() : "")
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> setPreferences(String firstName, String lastName,
            ByteString deviceId, String deviceName)
            throws Exception
    {
        _transaction.begin();

        if (firstName != null || lastName != null) {
            if (firstName == null || lastName == null)
                throw new ExBadArgs("First and last name must both be non-null or both null");
            _sessionUser.get().setName(new FullName(firstName, lastName));
        }

        if (deviceId != null) {
            _factDevice.create(deviceId).setName(deviceName);
        }

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListUsersReply> listUsers(String search, Integer maxResults,
            Integer offset)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();
        UserListAndQueryCount listAndCount = org.listUsers(search, maxResults, offset);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(userInfoList2PBUserList(listAndCount._userInfoList))
                .setFilteredCount(listAndCount._count)
                .setTotalCount(org.totalUserCount())
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<ListUsersReply> listUsersAuth(String search,
            PBAuthorizationLevel authLevel, Integer maxResults, Integer offset)
        throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();
        AuthorizationLevel level = AuthorizationLevel.fromPB(authLevel);

        UserListAndQueryCount listAndCount = org.listUsersAuth(search, level, maxResults, offset);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(userInfoList2PBUserList(listAndCount._userInfoList))
                .setFilteredCount(listAndCount._count)
                .setTotalCount(org.totalUserCount(level))
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    private static List<PBUser> userInfoList2PBUserList(List<UserInfo> uis)
    {
        List<PBUser> pbusers = Lists.newArrayListWithCapacity(uis.size());
        for (UserInfo ui : uis) {
            pbusers.add(PBUser.newBuilder()
                    .setUserEmail(ui._userId.toString())
                    .setFirstName(ui._firstName)
                    .setLastName(ui._lastName)
                    .build());
        }
        return pbusers;
    }

    @Override
    public ListenableFuture<ListSharedFoldersResponse> listSharedFolders(Integer maxResults,
            Integer offset)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();
        OrgID orgID = user.getOrganization().id();

        int sharedFolderCount = _organizationManagement.countSharedFolders(orgID);
        List<PBSharedFolder> sharedFolderList =
                _organizationManagement.listSharedFolders(orgID, maxResults, offset);

        _transaction.commit();

        ListSharedFoldersResponse response = ListSharedFoldersResponse.newBuilder()
                .addAllSharedFolders(sharedFolderList)
                .setTotalCount(sharedFolderCount)
                .build();

        return createReply(response);
    }

    @Override
    public ListenableFuture<Void> setAuthorizationLevel(final String userIdString,
            final PBAuthorizationLevel authLevel)
            throws Exception
    {
        _transaction.begin();

        User requester = _sessionUser.get();
        User subject = _factUser.createFromExternalID(userIdString);
        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        // Verify caller and subject's organization match
        if (!requester.getOrganization().id().equals(subject.getOrganization().id())) {
            throw new ExNoPerm("organization mismatch");
        }

        if (requester.id().equals(subject.id())) {
            throw new ExNoPerm("cannot change authorization for yourself");
        }

        if (requester.getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm(requester + " cannot change authorization");
        }

        // Verify caller's authorization level covers the new level
        if (!requester.getLevel().covers(newAuth)) {
            throw new ExNoPerm("cannot change authorization to " + authLevel);
        }

        subject.setLevel(newAuth);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> addOrganization(final String orgName)
            throws Exception
    {
        _transaction.begin();
        _sessionUser.get().addAndMoveToOrganization(orgName);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrgPreferencesReply> getOrgPreferences()
        throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();

        GetOrgPreferencesReply orgPreferences = GetOrgPreferencesReply.newBuilder()
                .setOrgName(org.getName())
                .build();

        _transaction.commit();

        return createReply(orgPreferences);
    }

    @Override
    public ListenableFuture<Void> setOrgPreferences(@Nullable String orgName)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();
        user.getOrganization().setName(orgName);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetUnsubscribeEmailReply> unsubscribeEmail(String unsubscribeToken)
            throws Exception
    {
        _transaction.begin();
        String email = _db.getEmail(unsubscribeToken);
        _db.removeEmailSubscription(unsubscribeToken);
        _transaction.commit();

        GetUnsubscribeEmailReply unsubscribeEmail = GetUnsubscribeEmailReply.newBuilder()
                .setEmailId(email)
                .build();

        return createReply(unsubscribeEmail);
    }

    @Override
    public ListenableFuture<GetAuthorizationLevelReply> getAuthorizationLevel()
            throws Exception
    {
        _transaction.begin();

        AuthorizationLevel level = _sessionUser.get().getLevel();

        _transaction.commit();

        return createReply(GetAuthorizationLevelReply.newBuilder().setLevel(level.toPB()).build());
    }

    @Override
    public ListenableFuture<GetTeamServerUserIDReply> getTeamServerUserID()
            throws SQLException, ExNoPerm, ExNotFound
    {
        _transaction.begin();

        User user = _sessionUser.get();

        // Create the organzation if necessary
        if (user.getOrganization().isDefault()) {
            user.addAndMoveToOrganization("An Awesome Team");
        } else if (!user.getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm("only admins can setup " + S.TEAM_SERVERS);
        }

        UserID tsUserID = user.getOrganization().id().toTeamServerUserID();

        GetTeamServerUserIDReply reply = GetTeamServerUserIDReply.newBuilder()
                .setId(tsUserID.toString())
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<CertifyDeviceReply> certifyTeamServerDevice(
            ByteString deviceId, ByteString csr)
            throws ExNoPerm, ExNotFound, ExAlreadyExist, SQLException, SignatureException,
            IOException, ExBadArgs, NoSuchAlgorithmException, CertificateException,
            ExDeviceIDAlreadyExists
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();

        Organization org = user.getOrganization();
        User tsUser = _factUser.create(org.id().toTeamServerUserID());

        // Create the team server user if not found
        if (!tsUser.exists()) {
            // Use an invalid password hash to prevent attackers from logging in as Team Server
            // using any password. Also see C.TEAM_SERVER_LOCAL_PASSWORD.
            tsUser.add(new byte[0], new FullName("Team", "Server"), org);
        }

        // Certify device
        Device device = _factDevice.create(deviceId);
        device.add(tsUser, UNKNOWN_NAME);
        CertifyDeviceReply reply = certifyDevice(csr, device);

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> emailUser(String userId, String body)
            throws Exception
    {
        _transaction.begin();

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SP_EMAIL_NAME,
                _sessionUser.getID().toString(), null, UserID.fromExternal(userId).toString(), body,
                null, true, null);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetHeartInvitesQuotaReply> getHeartInvitesQuota()
            throws Exception
    {
        _transaction.begin();

        GetHeartInvitesQuotaReply reply = GetHeartInvitesQuotaReply.newBuilder()
                .setCount(_db.getFolderlessInvitesQuota(_sessionUser.getID()))
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<CertifyDeviceReply> certifyDevice(final ByteString deviceId,
            final ByteString csr, final Boolean recertify)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        Device device = _factDevice.create(deviceId);

        // Test the device id's availability/validity
        if (recertify) {
            User owner = device.getOwner();
            if (!owner.equals(user)) {
                throw new ExNoPerm("Recertify a device by a different owner: " +
                        user + " != " + owner);
            }
        } else {
            device.add(user, UNKNOWN_NAME);
        }

        CertifyDeviceReply reply = certifyDevice(csr, device);

        _transaction.commit();

        return createReply(reply);
    }

    private CertifyDeviceReply certifyDevice(ByteString csr, Device device)
            throws SignatureException, IOException, NoSuchAlgorithmException, ExBadArgs, ExNotFound,
            ExAlreadyExist, SQLException, CertificateException
    {
        Certificate cert = device.certify(new PKCS10(csr.toByteArray()));
        return CertifyDeviceReply.newBuilder()
                .setCert(cert.toString())
                .build();
    }


    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<PBSubjectRolePair> rolePairs, @Nullable String note)
            throws Exception
    {
        SID sid = new SID(shareId);
        UserID userId = _sessionUser.getID();

        l.info("user:" + userId + " attempt to share folder with subjects:" + rolePairs.size());

        if (rolePairs.isEmpty()) throw new ExNoPerm("Must specify one or more sharee");

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(rolePairs);

        _transaction.begin();
        Map<UserID, Long> epochs =
                _sharedFolderManagement.shareFolder(folderName, sid, userId, srps, note, emailers);
        // send verkehr notification as part of the transaction
        publish_(epochs);
        _transaction.commit();

        l.info(userId + " completed share folder for s:" + sid);

        InvitationEmailer.sendAll(emailers);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<ResolveSharedFolderCodeReply> resolveSharedFolderCode(String code)
            throws Exception
    {
        _transaction.begin();

        l.info("shared folder code: " + code);
        User user = _sessionUser.get();

        FolderInvitation invitation = _db.getFolderInvitation(code);
        if (invitation != null) {

            if (!user.id().equals(invitation._invitee)) {
                throw new ExNoPerm("Your email " + user.id() + " does not match the expected " +
                        invitation._invitee);
            }

            ResolveSharedFolderCodeReply reply = ResolveSharedFolderCodeReply.newBuilder()
                    .setShareId(invitation._sid.toPB())
                    .setFolderName(invitation._folderName)
                    .build();

            // Because the folder code is valid and was received by email, the user is verified.
            user.setVerified();

            _transaction.commit();

            return createReply(reply);
        } else {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
    }

    @Override
    public ListenableFuture<ListPendingFolderInvitationsReply> listPendingFolderInvitations()
            throws Exception
    {
        _transaction.begin();

        User u = _sessionUser.get();

        List<FolderInvitation> invitations = _db.listPendingFolderInvitations(u.id());

        // Only throw ExNoPerm if user isn't verified AND there are shared folder invitations to accept
        if (!invitations.isEmpty() && !u.isVerified()) {
            throw new ExNoPerm("email address not verified");
        }

        _transaction.commit();

        ListPendingFolderInvitationsReply.Builder builder =
                ListPendingFolderInvitationsReply.newBuilder();
        for (FolderInvitation fi : invitations) {
            builder.addInvitations(PBFolderInvitation.newBuilder()
                    .setShareId(fi._sid.toPB())
                    .setFolderName(fi._folderName)
                    .setSharer(fi._invitee.toString()));
        }

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
    public ListenableFuture<ResolveTargetedSignUpCodeReply>
            resolveTargetedSignUpCode(String tsc)
            throws SQLException, ExNotFound
    {
        l.info("tsc: " + tsc);

        _transaction.begin();
        ResolveTargetedSignUpCodeResult result = _db.getTargetedSignUp(tsc);
        _transaction.commit();

        return createReply(ResolveTargetedSignUpCodeReply.newBuilder()
                .setEmailAddress(result._userId.toString())
                .build());
    }

    @Override
    public ListenableFuture<Void> inviteUser(List<String> userIdStrings,
            Boolean inviteToDefaultOrg)
            throws ExNoPerm, SQLException, ExNotFound, ExEmailSendingFailed, ExAlreadyExist,
                IOException
    {
        if (userIdStrings.isEmpty()) throw new ExNoPerm("Must specify one or more invitee");

        _transaction.begin();

        User user = _sessionUser.get();

        // check and set storeless invite quota
        int left = _db.getFolderlessInvitesQuota(user.id()) - userIdStrings.size();
        if (left < 0) {
            throw new ExNoPerm();
        } else {
            _db.setFolderlessInvitesQuota(user.id(), left);
        }

        Organization org = inviteToDefaultOrg ? _factOrg.getDefault() : user.getOrganization();

        // Invite all invitees to Organization "org"
        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();
        for (String inviteeString : userIdStrings) {
            UserID invitee = UserID.fromExternal(inviteeString);
            emailers.add(_userManagement.inviteOneUser(user, invitee, org, null, null));
        }

        _transaction.commit();

        InvitationEmailer.sendAll(emailers);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm
    {
        // TODO (WW) move business logic to SharedFolderManagement

        _transaction.begin();
        ACLReturn result = _db.getACL(epoch, _sessionUser.getID());
        _transaction.commit(); // commit right away to avoid holding read locks

        // this means that no acl changes have occurred
        if (result.getEpoch() == epoch) {
            l.info("no updates - matching epoch:" + epoch);
            return createReply(GetACLReply.newBuilder().setEpoch(epoch).build());
        }

        GetACLReply.Builder getACLBuilder = GetACLReply.newBuilder();
        for (Map.Entry<SID, List<SubjectRolePair>> entry : result.getSidToPairs().entrySet()) {
            PBStoreACL.Builder storeAclBuilder = PBStoreACL.newBuilder();

            l.info("add s:" + entry.getKey());

            storeAclBuilder.setStoreId(entry.getKey().toPB());
            for (SubjectRolePair pair : entry.getValue()) {
                l.info("add j:" + pair._subject + " r:" + pair._role.getDescription());
                storeAclBuilder.addSubjectRole(PBSubjectRolePair
                        .newBuilder()
                        .setSubject(pair._subject.toString())
                        .setRole(pair._role.toPB()));
            }

            getACLBuilder.addStoreAcl(storeAclBuilder);
        }
        getACLBuilder.setEpoch(result.getEpoch());

        return createReply(getACLBuilder.build());
    }

    @Override
    public ListenableFuture<Void> updateACL(final ByteString storeId,
            final List<PBSubjectRolePair> subjectRoleList)
            throws Exception
    {
        UserID userId = _sessionUser.getID();
        SID sid = new SID(storeId);
        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(subjectRoleList);

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        _transaction.begin();
        Map<UserID, Long> epochs = _sharedFolderManagement.updateACL(userId, sid, srps);
        // send verkehr notification as part of the transaction
        publish_(epochs);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId,
            final List<String> subjectList)
            throws Exception
    {
        UserID userId = _sessionUser.getID();
        SID sid = new SID(storeId.toByteArray());
        List<UserID> subjects = UserID.fromExternal(subjectList);

        _transaction.begin();
        Map<UserID, Long> updatedEpochs = _sharedFolderManagement.deleteACL(userId, sid, subjects);
        publish_(updatedEpochs);
        _transaction.commit();

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

    private void publish_(Map<UserID, Long> epochs)
            throws Exception
    {
        for (Map.Entry<UserID, Long> entry : epochs.entrySet()) {
            l.info("publish notification to " + entry.getKey());

            PBACLNotification notification = PBACLNotification
                    .newBuilder()
                    .setAclEpoch(entry.getValue())
                    .build();

            ListenableFuture<Void> published =
                    _verkehrPublisher.publish_(entry.getKey().toString(),
                    notification.toByteArray());

            verkehrFutureGet_(published);
        }
    }

    private void updateCRL_(ImmutableList<Long> serials)
            throws Exception
    {
        l.info("command verkehr, #serials: " + serials.size());
        ListenableFuture<Void> succeeded = _verkehrAdmin.updateCRL_(serials);
        verkehrFutureGet_(succeeded);
    }

    @Override
    public ListenableFuture<Void> signOut()
            throws Exception
    {
        _sessionUser.remove();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signIn(String userIdString, ByteString credentials)
            throws IOException, SQLException, ExBadCredential, ExNotFound
    {
        _transaction.begin();

        User user = _factUser.createFromExternalID(userIdString);

        // Bypass password check only for staging team servers.
        // TODO (WW) remove this check
        if (!(Cfg.staging() && user.id().isTeamServerID())) {
            user.signIn(SPParam.getShaedSP(credentials.toByteArray()));
        }

        _sessionUser.set(user);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signUp(String userIdString, ByteString credentials,
            String firstName, String lastName)
            throws ExNotFound, SQLException, ExAlreadyExist, ExBadArgs
    {
        if (!Util.isValidEmailAddress(userIdString)) throw new ExBadArgs("invalid email address");

        User user = _factUser.createFromExternalID(userIdString);
        FullName fullName = new FullName(firstName, lastName);

        _transaction.begin();
        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());
        user.add(shaedSP, fullName, _factOrg.getDefault());
        //unsubscribe user from the aerofs invitation reminder mailing list
        _db.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String userIdString)
            throws Exception
    {
        _transaction.begin();
        _userManagement.sendPasswordResetEmail(_factUser.createFromExternalID(userIdString));
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resetPassword(String password_reset_token,
            ByteString new_credentials)
        throws Exception
    {
        _transaction.begin();
        _userManagement.resetPassword(password_reset_token,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> changePassword(ByteString old_credentials,
            ByteString new_credentials)
            throws Exception
    {
        _transaction.begin();
        _userManagement.changePassword(_sessionUser.getID(),old_credentials,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }
    @Override
    public ListenableFuture<Void> signUpWithTargeted(String targetedInvite, ByteString credentials,
            String firstName, String lastName)
            throws SQLException, ExAlreadyExist, ExNotFound, ExBadArgs
    {
        l.info("targeted sign-up: " + targetedInvite);

        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());
        FullName fullName = new FullName(firstName, lastName);

        _transaction.begin();

        ResolveTargetedSignUpCodeResult result = _db.getTargetedSignUp(targetedInvite);
        User user = _factUser.create(result._userId);

        user.add(shaedSP, fullName, _factOrg.create(result._orgId));
        // Since no exceptions were thrown, and the signup code was received via email,
        // mark the user as verified
        user.setVerified();

        // TODO (WW) don't we need to unsubscribe the user from the reminder mailing list, similar
        // to signUp()?

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetUserCRLReply> getUserCRL(final Long crlEpoch)
        throws Exception
    {
        throw new UnsupportedOperationException();

        // TODO (MP) make db call

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

        _transaction.begin();
        crl = _certdb.getCRL();
        _transaction.commit();

        GetCRLReply reply = GetCRLReply.newBuilder()
                .addAllSerial(crl)
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> revokeDeviceCertificate(final ByteString deviceId)
        throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        Device device = _factDevice.create(deviceId);
        User owner = device.getOwner();

        if (!owner.equals(user)) {
            throw new ExNoPerm("Cannot revoke cert for device by a different owner: " +
                    user + " != " + owner);
        }

        ImmutableList<Long> serials = device.revokeCertificates();

        // Push revoked serials to verkehr.
        updateCRL_(serials);

        // TODO (MP) update security epochs using serial list.
        // TODO (MP) publish updated epochs to verkehr.

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> revokeUserCertificates()
        throws Exception
    {
        _transaction.begin();

        ImmutableList<Long> serials = _certdb.revokeUserCertificates(_sessionUser.getID());

        // Push revoked serials to verkehr.
        updateCRL_(serials);

        // TODO (MP) update security epochs using serial list.
        // TODO (MP) publish updated epochs to verkehr.

        _transaction.commit();

        return createVoidReply();
    }

    private static final PBDeviceInfo EMPTY_DEVICE_INFO = PBDeviceInfo.newBuilder().build();

    /**
     * Given a list of device IDs, this call will return a list of device info objects of the same
     * length.
     *
     * If we cannot find a given DID, return an empty device info object. If the session user does
     * not share anything with the owner of the given DID, also return an empty device info object.
     */
    @Override
    public ListenableFuture<GetDeviceInfoReply> getDeviceInfo(List<ByteString> dids) throws Exception
    {
        _transaction.begin();

        Set<UserID> sharedUsers = _db.getSharedUsersSet(_sessionUser.getID());

        GetDeviceInfoReply.Builder builder = GetDeviceInfoReply.newBuilder();
        for (ByteString did : dids) {
            DeviceInfo info = _db.getDeviceInfo(new DID(did));

            // If there is a permission error or the device does not exist, simply provide an empty
            // device info object.
            if (info != null && sharedUsers.contains(info._ownerID)) {
                builder.addDeviceInfo(PBDeviceInfo.newBuilder()
                    .setDeviceName(info._deviceName)
                    .setOwner(PBUser.newBuilder()
                        .setUserEmail(info._ownerID.toString())
                        .setFirstName(info._ownerFirstName)
                        .setLastName(info._ownerLastName)));
            } else {
                builder.addDeviceInfo(EMPTY_DEVICE_INFO);
            }
        }

        _transaction.commit();

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
}
