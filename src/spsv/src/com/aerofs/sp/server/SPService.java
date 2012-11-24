package com.aerofs.sp.server;

import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceNameAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.SPDatabase.DeviceInfo;
import com.aerofs.sp.server.lib.SPDatabase.ResolveTargetedSignUpCodeResult;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase.UserInfo;
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
import com.aerofs.proto.Sp.SignInReply;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.cert.Certificate;
import com.aerofs.sp.server.cert.ICertificateGenerator;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.sp.server.user.UserManagement.UserListAndQueryCount;
import com.aerofs.sp.server.lib.ACLReturn;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPDatabase.DeviceRow;
import com.aerofs.sp.server.lib.SPDatabase.FolderInvitation;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.ISessionUserID;
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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.aerofs.sp.server.SPParam.SP_EMAIL_NAME;

class SPService implements ISPService
{
    private static final Logger l = Util.l(SPService.class);

    // the temporary user or device name used before SetPreferences is called
    private static final String UNKNOWN_NAME = "(unknown)";

    private final SPDatabase _db;
    private final IThreadLocalTransaction<SQLException> _transaction;

    private VerkehrPublisher _verkehrPublisher;
    private VerkehrAdmin _verkehrAdmin;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.get() returns the userId associated with the current HttpSession.
    // Note that the session is set externally in SPServlet.
    private final ISessionUserID _sessionUser;

    private final UserManagement _userManagement;
    private final OrganizationManagement _organizationManagement;
    private final SharedFolderManagement _sharedFolderManagement;
    private final ICertificateGenerator _certificateGenerator;

    SPService(SPDatabase db, IThreadLocalTransaction<SQLException> transaction,
            ISessionUserID sessionUser, UserManagement userManagement,
            OrganizationManagement organizationManagement,
            SharedFolderManagement sharedFolderManagement,
            ICertificateGenerator certificateGenerator)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _transaction = transaction;
        _sessionUser = sessionUser;
        _userManagement = userManagement;
        _organizationManagement = organizationManagement;
        _sharedFolderManagement = sharedFolderManagement;
        _certificateGenerator = certificateGenerator;
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
        // Report error in logs and notify SPTransaction that an exception occurred
        l.warn("user: " + _sessionUser + ": " + Util.e(e));
        _transaction.handleException();

        // don't include stack trace here to avoid expose SP internals to the client side.
        return Exceptions.toPB(e);
    }

    @Override
    public ListenableFuture<GetPreferencesReply> getPreferences(ByteString deviceId)
            throws Exception
    {
        _transaction.begin();

        User user = _db.getUserNullable(_sessionUser.get());
        if (user == null) throw new ExNotFound();

        DeviceRow dr = _db.getDevice(new DID(deviceId));

        _transaction.commit();

        GetPreferencesReply reply = GetPreferencesReply.newBuilder()
                .setFirstName(user._firstName)
                .setLastName(user._lastName)
                .setDeviceName(dr == null ? "" : dr.getName())
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> setPreferences(String userFirstName, String userLastName,
            ByteString deviceId, String deviceName)
            throws Exception
    {
        _transaction.begin();

        if (userFirstName != null || userLastName != null) {
            if (userFirstName == null || userLastName == null)
                throw new ExBadArgs("First and last name must both be non-null or both null");
            _db.setUserName(_sessionUser.get(), userFirstName, userLastName);
        }
        if (deviceId != null) {
            while (true) {
                try {
                    _db.setDeviceInfo(new DID(deviceId), deviceName);
                    break;
                } catch (ExDeviceNameAlreadyExist e) {
                    deviceName = Util.nextName(deviceName, "");
                }
            }
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

        User user = _userManagement.getUser(_sessionUser.get());
        user.throwIfNotAdmin();

        OrgID orgId = user._orgID;

        UserListAndQueryCount listAndCount =
                _userManagement.listUsers(search, maxResults, offset, orgId);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(userInfoList2PBUserList(listAndCount._uis))
                .setFilteredCount(listAndCount._count)
                .setTotalCount(_userManagement.totalUserCount(orgId))
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

        User user = _userManagement.getUser(_sessionUser.get());
        user.throwIfNotAdmin();

        OrgID orgId = user._orgID;
        AuthorizationLevel level = AuthorizationLevel.fromPB(authLevel);

        UserListAndQueryCount listAndCount =
                _userManagement.listUsersAuth(search, level, maxResults, offset, orgId);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(userInfoList2PBUserList(listAndCount._uis))
                .setFilteredCount(listAndCount._count)
                .setTotalCount(_userManagement.totalUserCount(level, orgId))
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

        User user = _userManagement.getUser(_sessionUser.get());
        user.throwIfNotAdmin();

        int sharedFolderCount = _organizationManagement.countSharedFolders(user._orgID);
        List<PBSharedFolder> sharedFolderList =
                _organizationManagement.listSharedFolders(user._orgID, maxResults, offset);

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
        UserID userId = UserID.fromExternal(userIdString);

        _transaction.begin();

        User requester = _userManagement.getUser(_sessionUser.get());
        User subject = _userManagement.getUser(userId);

        // Verify caller and subject's organization match
        if (!requester._orgID.equals(subject._orgID))
            throw new ExNoPerm("Organization mismatch.");

        // Verify caller's authorization level dominates the subject's
        if (requester._level != AuthorizationLevel.ADMIN) throw new ExNoPerm(requester +
                " cannot change authorization of " + subject);

        if (requester._id.equals(subject._id)) {
            throw new ExNoPerm(requester +
                    " : cannot change authorization level for yourself");
        }

        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        // Verify caller's authorization level dominates or matches the new level
        if (!requester._level.covers(newAuth))
            throw new ExNoPerm(requester + " cannot change authorization to " + authLevel);

        _userManagement.setAuthorizationLevel(subject._id, newAuth);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> addOrganization(final String orgName)
            throws Exception
    {
        _transaction.begin();

        // TODO: verify the calling user is allowed to create an organization
        // (check with the payment system)

        User user = _userManagement.getUser(_sessionUser.get());

        // only users in the default organization or admins can add organizations.
        if ((!user._orgID.equals(OrgID.DEFAULT) || user._level != AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm("API meant for internal use by AeroFS employees only");
        }

        Organization org = _organizationManagement.addOrganization(orgName);

        _organizationManagement.moveUserToOrganization(user._id, org._id);
        _userManagement.setAuthorizationLevel(user._id, AuthorizationLevel.ADMIN);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrgPreferencesReply> getOrgPreferences()
        throws Exception
    {
        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.get());
        user.throwIfNotAdmin();

        Organization org = _organizationManagement.getOrganization(user._orgID);

        GetOrgPreferencesReply orgPreferences = GetOrgPreferencesReply.newBuilder()
                .setOrgName(org._name)
                .build();

        _transaction.commit();

        return createReply(orgPreferences);
    }

    @Override
    public ListenableFuture<Void> setOrgPreferences(@Nullable String orgName)
            throws Exception
    {
        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.get());
        user.throwIfNotAdmin();

        _organizationManagement.setOrganizationPreferences(user._orgID, orgName);

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
    public ListenableFuture<Void> emailUser(String userId, String body)
            throws Exception
    {
        _transaction.begin();

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SP_EMAIL_NAME,
                _sessionUser.get().toString(), null, UserID.fromExternal(userId).toString(), body,
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
                .setCount(_db.getFolderlessInvitesQuota(_sessionUser.get()))
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<CertifyDeviceReply> certifyDevice(final ByteString deviceId,
            final ByteString csrBytes, final Boolean recertify)
            throws Exception
    {
        _transaction.begin();

        UserID userId = _sessionUser.get();
        DID did = new DID(deviceId);

        // Test the device id's availability/validity
        if (recertify) {
            DeviceRow dr = _db.getDevice(did);
            if (dr == null) {
                throw new ExNotFound("Recertify a non-existing device: " + did);
            } else if (!dr.getOwnerID().equals(userId)) {
                throw new ExNoPerm("Recertify a device by a different owner: " +
                        userId + " != " + dr.getOwnerID());
            }
        } else {
            String deviceName = UNKNOWN_NAME;
            while (true) {
                try {
                    _db.addDevice(new DeviceRow(did, deviceName, userId));
                    break;
                } catch (ExDeviceNameAlreadyExist e) {
                    deviceName = Util.nextName(deviceName, "");
                }
            }

        }

        // Verify the device ID and user ID matches what is specified in CSR.
        PKCS10 csr = new PKCS10(csrBytes.toByteArray());
        String cname = csr.getSubjectName().getCommonName();

        if (!cname.equals(SecUtil.getCertificateCName(userId, did))) {
            throw new ExBadArgs("cname doesn't match: hash(" + userId + " + " +
                    did.toStringFormal() + ") != " + cname);
        }

        Certificate cert = _certificateGenerator.createCertificate(userId, did, csr);
        CertifyDeviceReply reply = CertifyDeviceReply.newBuilder().setCert(cert.toString()).build();

        // Create the required entry in the certificate table. If this operation fails then
        // the CA will still have a record of the certificate, but we will not return it.
        // This is okay, since the DRL (device revocation list) is maintained by the SP and
        // not the CA anyway.
        _db.addCertificate(cert.getSerial(), did, cert.getExpireTs());
        l.info("created certificate for " + did.toStringFormal() + " with serial " +
                cert.getSerial() + " (expires on " + cert.getExpireTs().toString() + ")");

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<PBSubjectRolePair> rolePairs, @Nullable String note)
            throws Exception
    {
        SID sid = new SID(shareId);
        UserID userId = _sessionUser.get();

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
        User u = _userManagement.getUser(_sessionUser.get());

        FolderInvitation invitation = _db.getFolderInvitation(code);
        if (invitation != null) {

            if (!u._id.equals(invitation._invitee)) {
                throw new ExNoPerm("Your email " + u._id + " does not match the expected " +
                        invitation._invitee);
            }

            ResolveSharedFolderCodeReply reply = ResolveSharedFolderCodeReply.newBuilder()
                    .setShareId(invitation._sid.toPB())
                    .setFolderName(invitation._folderName)
                    .build();

            // Because the folder code is valid and was received by email, the user is verified.
            _db.markUserVerified(u._id);

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

        User u = _userManagement.getUser(_sessionUser.get());

        List<FolderInvitation> invitations = _db.listPendingFolderInvitations(u._id);

        // Only throw ExNoPerm if user isn't verified AND there are shared folder invitations to accept
        if (!invitations.isEmpty() && !u._isVerified) {
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
            throws Exception
    {
        throw new NotImplementedException();
    }

    @Override
    public ListenableFuture<Void> verifyEmail(String verificationCode)
            throws Exception
    {
        throw new NotImplementedException();
    }

    @Override
    public ListenableFuture<ResolveTargetedSignUpCodeReply>
            resolveTargetedSignUpCode(String tsc) throws Exception
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
            throws Exception
    {
        if (userIdStrings.isEmpty()) throw new ExNoPerm("Must specify one or more invitee");

        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.get());

        // check and set storeless invite quota
        int left = _db.getFolderlessInvitesQuota(user._id) - userIdStrings.size();
        if (left < 0) {
            throw new ExNoPerm();
        } else {
            _db.setFolderlessInvitesQuota(user._id, left);
        }

        Organization org = _db.getOrganization(inviteToDefaultOrg ? OrgID.DEFAULT : user._orgID);

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
            throws Exception
    {
        _transaction.begin();
        ACLReturn result = _db.getACL(epoch, _sessionUser.get());
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
        if (subjectRoleList.isEmpty()) throw new ExNoPerm("Must specify one or more subjects");

        UserID userId = _sessionUser.get();
        SID sid = new SID(storeId);

        l.info("user:" + userId + " attempt update acl for subjects:" + subjectRoleList.size());

        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(subjectRoleList);

        _transaction.begin();
        Map<UserID, Long> epochs = _db.updateACL(userId, sid, srps);
        // send verkehr notification as part of the transaction
        publish_(epochs);
        _transaction.commit();

        l.info(userId + " completed update acl for s:" + sid);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId,
            final List<String> subjectList)
            throws Exception
    {
        //
        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update
        //

        _transaction.begin();

        UserID userId = _sessionUser.get();
        l.info("user:" + userId + " attempt set acl for subjects:" + subjectList.size());

        assert subjectList.size() > 0;

        // convert the pb message into the appropriate format to make the db call

        SID sid = new SID(storeId.toByteArray());
        Set<UserID> subjects = new HashSet<UserID>(subjectList.size());
        for (String subject : subjectList) {
            subjects.add(UserID.fromExternal(subject));

            l.info("delete role for s:" + sid + " j:" + subject);
        }

        // make the db call and publish the result via verkehr

        Map<UserID, Long> updatedEpochs = _db.deleteACL(userId, sid, subjects);
        publish_(updatedEpochs);
        l.info(userId + " completed delete acl for s:" + sid);

        // once publishing succeeds we consider ourselves done
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
    public ListenableFuture<SignInReply> signIn(String userIdString, ByteString credentials)
            throws IOException, SQLException, ExBadCredential
    {
        _transaction.begin();

        UserID userId = UserID.fromExternal(userIdString);
        User user;
        try {
            user = _userManagement.getUser(userId);
        } catch (ExNotFound e) {
            l.warn("user " + userId + " not found");
            // use the same exception as if the password is incorrect, to prevent brute-force
            // guessing of user ids.
            throw new ExBadCredential(S.BAD_CREDENTIAL);
        }

        l.info("sign in: " + user);

        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());
        if (Arrays.equals(user._shaedSP, shaedSP)) {
            _sessionUser.set(userId);
        } else {
            l.warn("bad passwd for " + userId);
            // use the same exception as if the user doesn't exist, to prevent brute-force guessing
            // of user ids.
            throw new ExBadCredential(S.BAD_CREDENTIAL);
        }

        _transaction.commit();

        SignInReply reply = SignInReply.newBuilder()
                .setAuthLevel(user._level.toPB())
                .build();

        return createReply(reply);
    }

    private void throwIfUserIDIsInvalid(UserID userId)
            throws ExBadArgs
    {
        if (!Util.isValidEmailAddress(userId.toString())) {
            throw new ExBadArgs("invalid user ID");
        }
    }

    private void signUpCommon(UserID userId, ByteString credentials, String firstName,
            String lastName, OrgID orgID)
            throws ExBadArgs, ExAlreadyExist, SQLException
    {
        // Only call this within the context of another transaction!!
        assert _transaction.isInTransaction();

        assert userId != null;

        // Create a User with
        // - email verification marked as false
        // - authorization set to lowest USER level
        User user = new User(userId, firstName, lastName, credentials, false, orgID,
                AuthorizationLevel.USER);

        l.info(user + " attempt signup");

        // Common enforcement checks
        throwIfUserIDIsInvalid(user._id);
        _userManagement.throwIfUserIdDoesNotExist(user._id);

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _db.addUser(user);

        //unsubscribe user from the aerofs invitation reminder mailing list
        _db.removeEmailSubscription(userId, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
    }

    @Override
    public ListenableFuture<Void> signUp(String userIdString, ByteString credentials,
            String firstName, String lastName)
            throws ExNotFound, SQLException, ExAlreadyExist, ExBadArgs
    {
        _transaction.begin();

        signUpCommon(UserID.fromExternal(userIdString), credentials, firstName, lastName, OrgID.DEFAULT);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String userIdString)
            throws Exception
    {
        _transaction.begin();
        _userManagement.sendPasswordResetEmail(UserID.fromExternal(userIdString));
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
        _userManagement.changePassword(_sessionUser.get(),old_credentials,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }
    @Override
    public ListenableFuture<Void> signUpWithTargeted(String targetedInvite, ByteString credentials,
            String firstName, String lastName)
            throws SQLException, ExAlreadyExist, ExNotFound, ExBadArgs
    {
        l.info("targeted sign-up: " + targetedInvite);

        _transaction.begin();

        ResolveTargetedSignUpCodeResult result = _db.getTargetedSignUp(targetedInvite);

        signUpCommon(result._userId, credentials, firstName, lastName, result._orgId);

        // Since no exceptions were thrown, and the signup code was received via email,
        // mark the user as verified
        _db.markUserVerified(result._userId);

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
        crl = _db.getCRL();
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

        UserID userId = _sessionUser.get();
        DID did = new DID(deviceId);

        DeviceRow dr = _db.getDevice(did);
        if (dr == null) {
            throw new ExNotFound("Cannot revoke cert for non-existing device: " + did);
        } else if (!dr.getOwnerID().equals(userId)) {
            throw new ExNoPerm("Cannot revoke cert for device by a different owner: " +
                    userId + " != " + dr.getOwnerID());
        }

        ImmutableList<Long> serials = _db.revokeDeviceCertificate(did);

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

        ImmutableList<Long> serials = _db.revokeUserCertificates(_sessionUser.get());

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

        Set<UserID> sharedUsers = _db.getSharedUsersSet(_sessionUser.get());

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
