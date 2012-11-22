package com.aerofs.sp.server;

import com.aerofs.lib.C;
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
import com.aerofs.proto.Sp.SignUpCall;
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

        User user = _db.getUserNullable(_sessionUser.getUser());
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
            _db.setUserName(_sessionUser.getUser(), userFirstName, userLastName);
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

        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        String orgId = user._orgId;

        UserListAndQueryCount listAndCount =
                _userManagement.listUsers(search, maxResults, offset, orgId);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(listAndCount.users)
                .setFilteredCount(listAndCount.count)
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

        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        String orgId = user._orgId;
        AuthorizationLevel level = AuthorizationLevel.fromPB(authLevel);


        UserListAndQueryCount listAndCount =
                _userManagement.listUsersAuth(search, level, maxResults, offset, orgId);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(listAndCount.users)
                .setFilteredCount(listAndCount.count)
                .setTotalCount(_userManagement.totalUserCount(level, orgId))
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<ListSharedFoldersResponse> listSharedFolders(Integer maxResults,
            Integer offset)
            throws Exception
    {
        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        int sharedFolderCount = _organizationManagement.countSharedFolders(user._orgId);
        List<PBSharedFolder> sharedFolderList =
                _organizationManagement.listSharedFolders(user._orgId, maxResults, offset);

        _transaction.commit();

        ListSharedFoldersResponse response = ListSharedFoldersResponse.newBuilder()
                .addAllSharedFolders(sharedFolderList)
                .setTotalCount(sharedFolderCount)
                .build();

        return createReply(response);
    }

    @Override
    public ListenableFuture<Void> setAuthorizationLevel(final String userEmail,
            final PBAuthorizationLevel authLevel)
            throws Exception
    {
        _transaction.begin();

        User callerUser = _userManagement.getUser(_sessionUser.getUser());
        User subjectUser = _userManagement.getUser(userEmail);

        // Verify caller and subject's organization match
        if (!callerUser._orgId.equals(subjectUser._orgId))
            throw new ExNoPerm("Organization mismatch.");

        // Verify caller's authorization level dominates the subject's
        if (callerUser._level != AuthorizationLevel.ADMIN) throw new ExNoPerm(callerUser +
                " cannot change authorization of " + subjectUser);

        if (callerUser._id.equals(subjectUser._id)) {
            throw new ExNoPerm(callerUser +
                    " : cannot change authorization level for yourself");
        }

        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        // Verify caller's authorization level dominates or matches the new level
        if (!callerUser._level.covers(newAuth))
            throw new ExNoPerm(callerUser + " cannot change authorization to " + authLevel);

        _userManagement.setAuthorizationLevel(subjectUser._id, newAuth);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> addOrganization(final String orgId, final String orgName,
            final Boolean shareExternal, final @Nullable String allowedDomain,
            final @Nullable SignUpCall newAdminAccount,
            final @Nullable String existingUserToMakeAdmin)
            throws Exception
    {
        _transaction.begin();

        // TODO: verify the calling user is allowed to create an organization
        // (check with the payment system)

        // currently only allow AeroFS employees who are also administrators of the AeroFS internal
        // organization to call this
        User caller = _userManagement.getUser(_sessionUser.getUser());
        if ((!caller._orgId.equals("aerofs.com")) || caller._level != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("API meant for internal use by AeroFS employees only");
        }

        _organizationManagement.addOrganization(orgId, orgName, shareExternal, allowedDomain);

        if (newAdminAccount != null && newAdminAccount.isInitialized()) {
            signUpCommon(newAdminAccount.getUserId(), newAdminAccount.getCredentials(),
                    newAdminAccount.getFirstName(), newAdminAccount.getLastName(),
                    newAdminAccount.getOrganizationId());
            _userManagement.setAuthorizationLevel(newAdminAccount.getUserId(),
                    AuthorizationLevel.ADMIN);
        } else if (existingUserToMakeAdmin != null) {
            _organizationManagement.moveUserToOrganization(existingUserToMakeAdmin, orgId);
            _userManagement.setAuthorizationLevel(
                    existingUserToMakeAdmin, AuthorizationLevel.ADMIN);
        } else {
            throw new ExBadArgs("Must set either newAdminAccount or existingUserToMakeAdmin");
        }

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrgPreferencesReply> getOrgPreferences()
        throws Exception
    {
        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        Organization org = _organizationManagement.getOrganization(user._orgId);

        GetOrgPreferencesReply orgPreferences = GetOrgPreferencesReply.newBuilder()
                .setOrgId(org._id)
                .setOrgAllowedDomain(org._allowedDomain)
                .setOrgAllowOpenSharing(org._shareExternally)
                .setOrgName(org._name)
                .build();

        _transaction.commit();

        return createReply(orgPreferences);
    }

    @Override
    public ListenableFuture<Void> setOrgPreferences(@Nullable String orgName,
            @Nullable Boolean orgAllowOpenSharing, @Nullable String orgAllowedDomain)
            throws Exception
    {
        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        _organizationManagement.setOrganizationPreferences(user._orgId, orgName, orgAllowedDomain,
                orgAllowOpenSharing);

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
    public ListenableFuture<Void> emailUser(String subject, String body)
            throws Exception
    {
        _transaction.begin();

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SP_EMAIL_NAME, _sessionUser.getUser(), null,
                subject, body, null, true,null);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetHeartInvitesQuotaReply> getHeartInvitesQuota()
            throws Exception
    {
        _transaction.begin();

        GetHeartInvitesQuotaReply reply = GetHeartInvitesQuotaReply.newBuilder()
                .setCount(_db.getFolderlessInvitesQuota(_sessionUser.getUser()))
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

        // FIXME: make the session user get() method normalize the result by default.
        String user = User.normalizeUserId(_sessionUser.getUser());
        DID did = new DID(deviceId);

        // Test the device id's availability/validity
        if (recertify) {
            DeviceRow dr = _db.getDevice(did);
            if (dr == null) {
                throw new ExNotFound("Recertify a non-existing device: " + did);
            } else if (!dr.getOwnerID().equals(user)) {
                throw new ExNoPerm("Recertify a device by a different owner: " +
                        user + " != " + dr.getOwnerID());
            }
        } else {
            String deviceName = UNKNOWN_NAME;
            while (true) {
                try {
                    _db.addDevice(new DeviceRow(did, deviceName, user));
                    break;
                } catch (ExDeviceNameAlreadyExist e) {
                    deviceName = Util.nextName(deviceName, "");
                }
            }

        }

        // Verify the device ID and user ID matches what is specified in CSR.
        PKCS10 csr = new PKCS10(csrBytes.toByteArray());
        String cname = csr.getSubjectName().getCommonName();

        if (!cname.equals(SecUtil.getCertificateCName(user, did))) {
            throw new ExBadArgs("cname doesn't match: hash(" + user + " + " +
                    did.toStringFormal() + ") != " + cname);
        }

        Certificate cert = _certificateGenerator.createCertificate(user, did, csr);
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
        String user = _sessionUser.getUser();

        l.info("user:" + user + " attempt to share folder with subjects:" + rolePairs.size());

        if (rolePairs.isEmpty()) throw new ExNoPerm("Must specify one or more sharee");

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();

        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(rolePairs);

        _transaction.begin();
        Map<String, Long> epochs =
                _sharedFolderManagement.shareFolder(folderName, sid, user, srps, note, emailers);
        // send verkehr notification as part of the transaction
        publish_(epochs);
        _transaction.commit();

        l.info(user + " completed share folder for s:" + sid);

        InvitationEmailer.sendAll(emailers);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<ResolveSharedFolderCodeReply> resolveSharedFolderCode(String code)
            throws Exception
    {
        _transaction.begin();

        l.info("shared folder code: " + code);
        User u = _userManagement.getUser(_sessionUser.getUser());

        FolderInvitation invitation = _db.getFolderInvitation(code);
        if (invitation != null) {

            if (!u._id.equalsIgnoreCase(invitation.getInvitee())) {
                throw new ExNoPerm("Your email " + u._id + " does not match the expected " +
                        invitation.getInvitee());
            }

            ResolveSharedFolderCodeReply reply = ResolveSharedFolderCodeReply.newBuilder()
                    .setShareId(invitation.getSid().toPB())
                    .setFolderName(invitation.getFolderName())
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

        User u = _userManagement.getUser(_sessionUser.getUser());

        List<PBFolderInvitation> invitations = _db.listPendingFolderInvitations(u._id);

        // Only throw ExNoPerm if user isn't verified AND there are shared folder invitations to accept
        if (!invitations.isEmpty() && !u._isVerified) {
            throw new ExNoPerm("email address not verified");
        }

        _transaction.commit();

        return createReply(ListPendingFolderInvitationsReply.newBuilder()
                .addAllInvitations(invitations)
                .build());
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
        ResolveTargetedSignUpCodeReply reply = _db.getTargetedSignUp(tsc);
        _transaction.commit();

        if (reply != null) {
            return createReply(reply);
        } else {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
    }

    @Override
    public ListenableFuture<Void> inviteUser(List<String> emailAddresses,
            Boolean inviteToDefaultOrg)
            throws Exception
    {
        if (emailAddresses.isEmpty()) throw new ExNoPerm("Must specify one or more invitee");

        _transaction.begin();

        User user = _userManagement.getUser(_sessionUser.getUser());

        // check and set storeless invite quota
        int left = _db.getFolderlessInvitesQuota(user._id) - emailAddresses.size();
        if (left < 0) {
            throw new ExNoPerm();
        } else {
            _db.setFolderlessInvitesQuota(user._id, left);
        }

        Organization org = _db.getOrganization(inviteToDefaultOrg ?
                C.DEFAULT_ORGANIZATION : user._orgId);

        // Invite all invitees to Organization "org"
        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();
        for (String invitee : emailAddresses) {
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
        ACLReturn result = _db.getACL(epoch, _sessionUser.getUser());
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
                        .setSubject(pair._subject)
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

        String user = _sessionUser.getUser();
        SID sid = new SID(storeId);

        l.info("user:" + user + " attempt update acl for subjects:" + subjectRoleList.size());

        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(subjectRoleList);

        _transaction.begin();
        Map<String, Long> epochs = _db.updateACL(user, sid, srps);
        // send verkehr notification as part of the transaction
        publish_(epochs);
        _transaction.commit();

        l.info(user + " completed update acl for s:" + sid);

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

        String user = _sessionUser.getUser();
        l.info("user:" + user + " attempt set acl for subjects:" + subjectList.size());

        assert subjectList.size() > 0;

        // convert the pb message into the appropriate format to make the db call

        SID sid = new SID(storeId.toByteArray());
        Set<String> subjects = new HashSet<String>(subjectList.size());
        for (String subject : subjectList) {
            boolean added = subjects.add(subject);
            assert added;

            l.info("delete role for s:" + sid + " j:" + subject);
        }

        // make the db call and publish the result via verkehr

        Map<String, Long> updatedEpochs = _db.deleteACL(user, sid, subjects);
        publish_(updatedEpochs);
        l.info(user + " completed delete acl for s:" + sid);

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

    private void publish_(Map<String, Long> epochs)
            throws Exception
    {
        for (Map.Entry<String, Long> entry : epochs.entrySet()) {
            l.info("publish notification to " + entry.getKey());

            PBACLNotification notification = PBACLNotification
                    .newBuilder()
                    .setAclEpoch(entry.getValue())
                    .build();

            ListenableFuture<Void> published =
                    _verkehrPublisher.publish_(entry.getKey(), notification.toByteArray());

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
        _sessionUser.removeUser();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<SignInReply> signIn(String userId, ByteString credentials)
            throws IOException, SQLException, ExBadCredential
    {
        _transaction.begin();

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
            _sessionUser.setUser(userId);
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

    private void checkValidUserID(String userId)
            throws ExAlreadyExist
    {
        if (!Util.isValidEmailAddress(userId)) {
            l.warn(Util.quote(userId) + " invalid user id format");
            throw new ExAlreadyExist();
        }
    }

    /**
     * Only call this within the context of another transaction!!
     */
    private void signUpCommon(String userId, ByteString credentials, String firstName,
            String lastName, String orgId)
            throws ExAlreadyExist, SQLException, IOException
    {
        assert userId != null;
        assert orgId != null;

        // Create a User with
        // - normalized userID
        // - email verification marked as false
        // - authorization set to lowest USER level
        String normalizedUserId = User.normalizeUserId(userId);
        User user = new User(normalizedUserId, firstName, lastName, credentials, false,
                orgId, AuthorizationLevel.USER);

        l.info(user + " attempt signup");

        // Common enforcement checks
        checkValidUserID(user._id);
        _userManagement.checkUserIdDoesNotExist(user._id);

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _db.addUser(user);

        //unsubscribe user from the aerofs invitation reminder mailing list
        _db.removeEmailSubscription(userId, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
    }

    @Override
    public ListenableFuture<Void> signUp(String userId, ByteString credentials, String firstName,
            String lastName, String orgId)
            throws ExNotFound, SQLException, ExNoPerm, ExAlreadyExist, IOException
    {
        _transaction.begin();

        Organization org = _db.getOrganization(orgId);
        if (!org.domainMatches(userId)) {
            throw new ExNoPerm("Email domain does not match " + org._allowedDomain);
        }

        signUpCommon(userId, credentials, firstName, lastName, orgId);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String user_email)
            throws Exception
    {
        _transaction.begin();
        _userManagement.sendPasswordResetEmail(user_email);
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
        _userManagement.changePassword(_sessionUser.getUser(),old_credentials,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }
    @Override
    public ListenableFuture<Void> signUpWithTargeted(String targetedInvite, ByteString credentials,
            String firstName, String lastName)
            throws SQLException, ExAlreadyExist, IOException, ExNotFound
    {
        l.info("targeted sign-up: " + targetedInvite);

        _transaction.begin();

        // Verify the sign up code is legitimate (i.e. found in the DB)
        ResolveTargetedSignUpCodeReply invitation = _db.getTargetedSignUp(targetedInvite);
        if (invitation == null) {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }

        String userId = invitation.getEmailAddress();
        String orgId = invitation.getOrganizationId();

        signUpCommon(userId, credentials, firstName, lastName, orgId);

        // Since no exceptions were thrown, and the signup code was received via email,
        // mark the user as verified
        _db.markUserVerified(userId);

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

        String user = User.normalizeUserId(_sessionUser.getUser());
        DID did = new DID(deviceId);

        DeviceRow dr = _db.getDevice(did);
        if (dr == null) {
            throw new ExNotFound("Cannot revoke cert for non-existing device: " + did);
        } else if (!dr.getOwnerID().equals(user)) {
            throw new ExNoPerm("Cannot revoke cert for device by a different owner: " +
                    user + " != " + dr.getOwnerID());
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

        String user = User.normalizeUserId(_sessionUser.getUser());
        ImmutableList<Long> serials = _db.revokeUserCertificates(user);

        // Push revoked serials to verkehr.
        updateCRL_(serials);

        // TODO (MP) update security epochs using serial list.
        // TODO (MP) publish updated epochs to verkehr.

        _transaction.commit();

        return createVoidReply();
    }

    private static PBDeviceInfo _emptyDeviceInfo = PBDeviceInfo.newBuilder().build();

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

        String user = User.normalizeUserId(_sessionUser.getUser());
        Set<String> sharedUsers = _db.getSharedUsersSet(user);

        GetDeviceInfoReply.Builder builder = GetDeviceInfoReply.newBuilder();
        for (ByteString did : dids) {
            PBDeviceInfo info = _db.getDeviceInfo(new DID(did));

            // If there is a permission error or the device does not exist, simply provide an empty
            // device info object.
            if (info != null && sharedUsers.contains(info.getOwner().getUserEmail())) {
                builder.addDeviceInfo(info);
            } else {
                builder.addDeviceInfo(_emptyDeviceInfo);
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
