package com.aerofs.sp.server.sp;

import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_ADDRESS;
import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_NAME;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;
import javax.mail.internet.MimeMessage;

import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.aerofs.proto.Sp.ListSharedFoldersResponse;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.srvlib.db.AbstractDatabaseTransaction;
import com.aerofs.srvlib.db.AbstractDatabase;
import com.aerofs.srvlib.sp.SPDatabase;
import com.aerofs.srvlib.sp.SPDatabase.DeviceRow;
import com.aerofs.srvlib.sp.SPDatabase.FolderInvitation;
import com.aerofs.srvlib.sp.user.AuthorizationLevel;
import com.aerofs.srvlib.sp.user.ISessionUserID;
import com.aerofs.srvlib.sp.user.User;
import com.aerofs.srvlib.sp.SPParam;
import com.aerofs.srvlib.sp.organization.Organization;
import com.aerofs.srvlib.sp.ACLReturn;
import org.apache.log4j.Logger;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import sun.security.pkcs.PKCS10;

import com.aerofs.lib.C;
import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sp.CertifyDeviceReply;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.proto.Sp.GetCRLReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetHeartInvitesQuotaReply;
import com.aerofs.proto.Sp.GetPreferencesReply;
import com.aerofs.proto.Sp.GetUserCRLReply;
import com.aerofs.proto.Sp.ISPService;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import com.aerofs.proto.Sp.ListUsersReply;
import com.aerofs.proto.Sp.PBACLNotification;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.proto.Sp.ResolveSharedFolderCodeReply;
import com.aerofs.proto.Sp.ResolveTargetedSignUpCodeReply;
import com.aerofs.proto.Sp.SignInReply;
import com.aerofs.sp.server.email.EmailUtil;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.sp.cert.Certificate;
import com.aerofs.sp.server.sp.cert.ICertificateGenerator;
import com.aerofs.sp.server.sp.organization.OrganizationManagement;
import com.aerofs.sp.server.sp.user.UserManagement;
import com.aerofs.sp.server.sp.user.UserManagement.UserListAndQueryCount;
import com.aerofs.verkehr.client.commander.VerkehrCommander;
import com.aerofs.verkehr.client.publisher.VerkehrPublisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

class SPService implements ISPService
{
    private static final Logger l = Util.l(SPService.class);

    // the temporary user or device name used before SetPreferences is called
    private static final String UNKNOWN_NAME = "(unknown)";

    private final SPDatabase _db;
    private VerkehrPublisher _verkehrPublisher;
    private VerkehrCommander _verkehrCommander;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.get() returns the userId associated with the current HttpSession.
    // Note that the session is set externally in SPServlet.
    private final ISessionUserID _sessionUser;

    private final UserManagement _userManagement;
    private final OrganizationManagement _organizationManagement;
    private final InvitationEmailer _invitationEmailer;
    private final ICertificateGenerator _certificateGenerator;

    SPService(SPDatabase db, ISessionUserID sessionUser, UserManagement userManagement,
            OrganizationManagement organizationManagement, InvitationEmailer invitationEmailer,
            ICertificateGenerator certificateGenerator)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _sessionUser = sessionUser;
        _userManagement = userManagement;
        _organizationManagement = organizationManagement;
        _invitationEmailer = invitationEmailer;
        _certificateGenerator = certificateGenerator;
    }

    public void setVerkehrClients_(VerkehrPublisher verkehrPublisher,
            VerkehrCommander verkehrCommander)
    {
        assert verkehrPublisher != null : ("cannot set null verkehr publisher client");
        assert verkehrCommander != null : ("cannot set null verkehr commander client");

        _verkehrPublisher = verkehrPublisher;
        _verkehrCommander = verkehrCommander;
    }

    @Override
    public PBException encodeError(Throwable e)
    {
        l.warn("user: " + _sessionUser + ": " + Util.e(e));
        // don't include stack trace here to avoid expose SP internals to the client side.
        return Exceptions.toPB(e);
    }

    @Override
    public ListenableFuture<GetPreferencesReply> getPreferences(ByteString deviceId)
            throws Exception
    {
        User ur = _db.getUser(_sessionUser.getUser());
        if (ur == null) throw new ExNotFound();

        DeviceRow dr = _db.getDevice(new DID(deviceId));

        GetPreferencesReply reply = GetPreferencesReply.newBuilder()
                .setFirstName(ur._firstName)
                .setLastName(ur._lastName)
                .setDeviceName(dr == null ? "" : dr.getName())
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> setPreferences(String userFirstName, String userLastName,
            ByteString deviceId, String deviceName)
            throws Exception
    {
        if (userFirstName != null || userLastName != null) {
            if (userFirstName == null || userLastName == null)
                throw new ExBadArgs("First and last name must both be non-null or both null");
            _db.setUserName(_sessionUser.getUser(), userFirstName, userLastName);
        }
        if (deviceId != null) {
            _db.setDeviceName(_sessionUser.getUser(), new DID(deviceId), deviceName);
        }

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListUsersReply> listUsers(String search, Integer maxResults,
            Integer offset)
            throws Exception
    {
        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        ListUsersReply reply;
        String orgId = user._orgId;
        if (search == null || search.equals("")) {
            // Return a subset of all users
            int totalCount = _userManagement.totalUserCount(orgId);
            reply = ListUsersReply.newBuilder()
                    .addAllUsers(_userManagement.listAllUsers(maxResults, offset, orgId))
                    .setTotalCount(totalCount)
                    .setFilteredCount(totalCount)
                    .build();
        } else {
            // Filter using the search parameter, and return a subset
            UserListAndQueryCount listAndCount =
                    _userManagement.listUsers(search, maxResults, offset, orgId);
            reply = ListUsersReply.newBuilder()
                    .addAllUsers(listAndCount.users)
                    .setFilteredCount(listAndCount.count)
                    .setTotalCount(_userManagement.totalUserCount(orgId))
                    .build();
        }
        return createReply(reply);
    }

    @Override
    public ListenableFuture<ListSharedFoldersResponse> listSharedFolders(Integer maxResults,
            Integer offset)
            throws Exception
    {
        User user = _userManagement.getUser(_sessionUser.getUser());
        user.verifyIsAdmin();

        int sharedFolderCount = _organizationManagement.countSharedFolders(user._orgId);
        List<PBSharedFolder> sharedFolderList =
                _organizationManagement.listSharedFolders(user._orgId, maxResults, offset);

        ListSharedFoldersResponse response = ListSharedFoldersResponse.newBuilder()
                .addAllSharedFolders(sharedFolderList)
                .setTotalCount(sharedFolderCount)
                .build();
        return createReply(response);
    }

    @Override
    public ListenableFuture<Void> setAuthorizationLevel(String userEmail,
            PBAuthorizationLevel authLevel)
            throws Exception
    {
        User callerUser = _userManagement.getUser(_sessionUser.getUser());
        User subjectUser = _userManagement.getUser(userEmail);

        // Verify caller and subject's organization match
        if (!callerUser._orgId.equals(subjectUser._orgId))
            throw new ExNoPerm("Organization mismatch.");

        // Verify caller's authorization level dominates the subject's
        if (!callerUser._level.dominates(subjectUser._level))
            throw new ExNoPerm(callerUser + " cannot change authorization of " + subjectUser);

        AuthorizationLevel newAuth = AuthorizationLevel.fromPB(authLevel);

        // Verify caller's authorization level dominates or matches the new level
        if (!callerUser._level.covers(newAuth))
            throw new ExNoPerm(callerUser + " cannot change authorization to " + authLevel);

        _userManagement.setAuthorizationLevel(subjectUser._id, newAuth);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> addOrganization(String orgId,
            String orgName, Boolean shareExternal, @Nullable String allowedDomain)
            throws Exception
    {
        String callerUser = _sessionUser.getUser();

        // TODO: verify callerUser is allowed to create an organization (check with payment system)

        _organizationManagement.addOrganization(orgId, orgName, shareExternal, allowedDomain,
                callerUser);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> emailUser(String subject, String body)
            throws Exception
    {
        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS, SP_EMAIL_NAME,
                _sessionUser.getUser(), null, subject, body);
        EmailUtil.sendEmail(msg, true);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetHeartInvitesQuotaReply> getHeartInvitesQuota()
            throws Exception
    {
        GetHeartInvitesQuotaReply reply = GetHeartInvitesQuotaReply.newBuilder()
                .setCount(_db.getFolderlessInvitesQuota(_sessionUser.getUser()))
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<CertifyDeviceReply> certifyDevice(final ByteString deviceId,
            final ByteString csrBytes, final Boolean recertify)
            throws Exception
    {
        AbstractDatabaseTransaction<ListenableFuture<CertifyDeviceReply>> trans = new
                AbstractDatabaseTransaction<ListenableFuture<CertifyDeviceReply>>(_db)
        {
            @Override
            protected ListenableFuture<CertifyDeviceReply> impl_(AbstractDatabase db,
                    AbstractDatabaseTransaction<ListenableFuture<CertifyDeviceReply>> trans)
                    throws Exception
            {
                SPDatabase spdb = (SPDatabase) db;

                // FIXME: make the session user get() method normalize the result by default.
                String user = User.normalizeUserId(_sessionUser.getUser());
                DID did = new DID(deviceId);

                // Test the device id's availability/validity
                if (recertify) {
                    DeviceRow dr = spdb.getDevice(did);
                    if (dr == null) {
                        throw new ExNotFound("Recertify a non-existing device: " + did);
                    } else if (!dr.getOwnerID().equals(user)) {
                        throw new ExNoPerm("Recertify a device by a different owner: " +
                                user + " != " + dr.getOwnerID());
                    }
                } else {
                    spdb.addDevice(new DeviceRow(did, UNKNOWN_NAME, user));
                }

                // Verify the device ID and user ID matches what is specified in CSR.
                PKCS10 csr = new PKCS10(csrBytes.toByteArray());
                String cname = csr.getSubjectName().getCommonName();

                if (!cname.equals(SecUtil.getCertificateCName(user, did))) {
                    throw new ExBadArgs("cname doesn't match: hash(" + user +
                            " + " + did.toStringFormal() + ") != " + cname);
                }

                Certificate cert = _certificateGenerator.createCertificate(user, did, csr);
                CertifyDeviceReply reply = CertifyDeviceReply.newBuilder()
                        .setCert(cert.toString())
                        .build();

                // Create the required entry in the certificate table. If this operation fails then
                // the CA will still have a record of the certificate, but we will not return it.
                // This is okay, since the DRL (device revocation list) is maintained by the SP and
                // not the CA anyway.
                spdb.addCertificate(cert.getSerial(), did, cert.getExpireTs());
                l.info("created certificate for " + did.toStringFormal() + " with serial " +
                        cert.getSerial() + " (expires on " + cert.getExpireTs().toString() + ")");

                trans.commit_();
                return createReply(reply);
            }
        };

        return trans.run_();
    }

    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<String> emailAddresses, String note)
            throws Exception
    {
        User sharer = _userManagement.getUser(_sessionUser.getUser());

        // Check that the user is verified - only verified users can share
        if (!sharer._isVerified) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm();
        }

        // TODO could look up sharer Organization once, outside the loop.
        // But if supporting list of email addresses is temporary, don't bother.
        for (String sharee : emailAddresses) {
            shareFolderWithOne(sharer, sharee, folderName, shareId.toByteArray(), note);
        }

        return createVoidReply();
    }

    private void shareFolderWithOne(User sharer, String shareeEmail, String folderName,
            byte[] shareId, @Nullable String note)
            throws Exception
    {
        Organization sharerOrg = _db.getOrganization(sharer._orgId);
        Organization shareeOrg;

        User sharee = _db.getUser(shareeEmail);
        if (sharee == null) {  // Sharing with a non-AeroFS user.
            boolean domainMatch = sharerOrg.domainMatches(shareeEmail);
            shareeOrg = _db.getOrganization(domainMatch ? sharerOrg._id : C.DEFAULT_ORGANIZATION);
            createFolderInvitation(sharer._id, shareeEmail, sharerOrg, shareeOrg, shareId,
                    folderName);
            inviteOneUser(sharer, shareeEmail, shareeOrg, folderName, note);
        } else {
            shareeOrg = _db.getOrganization(sharee._orgId);
            String code = createFolderInvitation(sharer._id, sharee._id, sharerOrg, shareeOrg,
                    shareId, folderName);
            _invitationEmailer.sendFolderInvitationEmail(sharer._id, sharee._id, sharer._firstName,
                    folderName, note, code);
        }

        l.info("folder sharer " + sharer + " sharee " + shareeEmail);
    }

    /**
     * Creates a folder invitation in the database
     * @return the share folder code
     * @throws ExNoPerm if either the sharer's or sharee's organization does not permit external
     *                  sharing (and they are not the same organization)
     */
    private String createFolderInvitation(String sharerId, String shareeId,
            Organization sharerOrg, Organization shareeOrg, byte[] sid, String folderName)
            throws ExNoPerm, SQLException, ExNotFound
    {
        if (!sharerOrg.equals(shareeOrg)) {
            if (!sharerOrg._shareExternally) {
                throw new ExNoPerm("Sharing outside this organization is not permitted");
            } else if (!shareeOrg._shareExternally) {
                throw new ExNoPerm("Sharing outside the organization " + shareeOrg
                        + " is not permitted");
            }
        }

        String code = InvitationCode.generate(CodeType.SHARE_FOLDER);

        _db.addShareFolderCode(code, sharerId, shareeId, sid, folderName);

        return code;
    }

    @Override
    public ListenableFuture<ResolveSharedFolderCodeReply> resolveSharedFolderCode(String code)
            throws Exception
    {
        l.info("shared folder code: " + code);
        User u = _userManagement.getUser(_sessionUser.getUser());

        FolderInvitation invitation = _db.getFolderInvitation(code);
        if (invitation != null) {

            if (!u._id.equalsIgnoreCase(invitation.getInvitee())) {
                throw new ExNoPerm("Your email " + u._id + " does not match the expected " +
                        invitation.getInvitee());
            }

            ResolveSharedFolderCodeReply reply = ResolveSharedFolderCodeReply.newBuilder()
                    .setShareId(ByteString.copyFrom(invitation.getSid()))
                    .setFolderName(invitation.getFolderName())
                    .build();

            // Because the folder code is valid and was received by email, the user is verified.
            _db.markUserVerified(u._id);

            return createReply(reply);
        } else {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
    }

    @Override
    public ListenableFuture<ListPendingFolderInvitationsReply> listPendingFolderInvitations()
            throws Exception
    {
        User u = _userManagement.getUser(_sessionUser.getUser());

        List<PBFolderInvitation> invitations = _db.listPendingFolderInvitations(u._id);

        // Only throw ExNoPerm if user isn't verified AND there are shared folder invitations to accept
        if (!invitations.isEmpty() && !u._isVerified) {
            throw new ExNoPerm("email address not verified");
        }

        return createReply(ListPendingFolderInvitationsReply.newBuilder()
                .addAllInvitations(invitations)
                .build());
    }

    @Override
    public ListenableFuture<Void> verifyBatchSignUpCode(String bsc)
            throws Exception
    {
        if (!_db.isValidBatchSignUp(bsc)) {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
        return createVoidReply();
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
        ResolveTargetedSignUpCodeReply reply = _db.getTargetedSignUp(tsc);
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
        for (String invitee : emailAddresses) {
            inviteOneUser(user, invitee, org, null, null);
        }

        return createVoidReply();
    }

    private void inviteOneUser(User inviter, String inviteeId, Organization inviteeOrg,
            @Nullable String folderName, @Nullable String note)
            throws Exception
    {
        assert inviteeId != null;

        // TODO could change userId field in DB to be case-insensitive to avoid normalization
        inviteeId = User.normalizeUserId(inviteeId);

        // Check that the invitee doesn't exist already
        _userManagement.checkUserIdDoesNotExist(inviteeId);

        // USER-level inviters can only invite to an organization that matches the domain
        if (inviter._level.equals(AuthorizationLevel.USER)
                && !inviteeOrg.domainMatches(inviteeId)) {
            throw new ExNoPerm(inviter._id + " cannot invite + " + inviteeId
                    + " to " + inviteeOrg._id);
        }

        String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);

        _db.addTargetedSignupCode(code, inviter._id, inviteeId, inviteeOrg._id);

        _invitationEmailer.sendUserInvitationEmail(inviter._id, inviteeId, inviter._firstName,
                folderName, note, code);

    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws Exception
    {
        ACLReturn result = _db.getACL(epoch, _sessionUser.getUser());

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
    public ListenableFuture<Void> setACL(final ByteString storeId,
            final List<PBSubjectRolePair> subjectRoleList)
            throws Exception
    {
        //
        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update
        //

        AbstractDatabaseTransaction<ListenableFuture<Void>> trans =
                new AbstractDatabaseTransaction<ListenableFuture<Void>>(_db)
        {
            @Override
            protected ListenableFuture<Void> impl_(AbstractDatabase db,
                    AbstractDatabaseTransaction<ListenableFuture<Void>> trans)
                    throws Exception
            {
                SPDatabase spdb = (SPDatabase) db;
                String user = _sessionUser.getUser();

                l.info("user:" + user + " attempt set acl for subjects:" + subjectRoleList.size());

                assert subjectRoleList.size() > 0;

                // convert the pb message into the appropriate format to make the db call

                SID sid = new SID(storeId);
                List<SubjectRolePair> pairs = Lists.newArrayListWithCapacity(
                        subjectRoleList.size());
                for (PBSubjectRolePair pbPair : subjectRoleList) {
                    SubjectRolePair pair = new SubjectRolePair(pbPair);
                    pairs.add(pair);

                    l.info("add role for s:" + sid + " j:" + pair._subject + " r:" + pair._role);
                }

                // make the db call and publish the result via verkehr

                Map<String, Long> updatedEpochs = spdb.setACL(user, sid, pairs);
                publish_(updatedEpochs);
                l.info(user + " completed set acl for s:" + sid);
                trans.commit_(); // after publishing, committing is considered done

                return createVoidReply();
            }
        };

        return trans.run_();
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

        AbstractDatabaseTransaction<ListenableFuture<Void>> trans =
                new AbstractDatabaseTransaction<ListenableFuture<Void>>(_db)
        {
            @Override
            protected ListenableFuture<Void> impl_(AbstractDatabase db,
                    AbstractDatabaseTransaction<ListenableFuture<Void>> trans)
                    throws Exception
            {
                SPDatabase spdb = (SPDatabase) db;
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

                Map<String, Long> updatedEpochs = spdb.deleteACL(user, sid, subjects);
                publish_(updatedEpochs);
                l.info(user + " completed delete acl for s:" + sid);
                trans.commit_(); // once publishing succeeds we consider ourselves done

                return createVoidReply();
            }
        };

        return trans.run_();
    }

    /**
     * Utility to minimize duped code in the below verkehr-related methods.
     * @param future either the verkehr publisher or commander.
     */
    private void verkehrFutureGet_(ListenableFuture<java.lang.Void> future)
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

            ListenableFuture<java.lang.Void> published =
                    _verkehrPublisher.publish_(entry.getKey(), notification.toByteArray());

            verkehrFutureGet_(published);
        }
    }

    private void updateCRL_(ImmutableList<Long> serials)
            throws Exception
    {
        l.info("command verkehr, #serials: " + serials.size());
        ListenableFuture<java.lang.Void> succeeded = _verkehrCommander.updateCRL_(serials);
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

        SignInReply reply = SignInReply.newBuilder()
                .setAuthLevel(user._level.toPB())
                .build();

        return createReply(reply);
    }

    private void checkValidUserID(String userId)
            throws ExAlreadyExist
    {
        if (!Util.isValidEmailAddress(userId)) {
            l.warn(Util.q(userId) + " invalid user id format");
            throw new ExAlreadyExist();
        }
    }

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
        // - finalized state set to true
        String normalizedUserId = User.normalizeUserId(userId);
        User user = new User(normalizedUserId, firstName, lastName, credentials, true, false,
                orgId, AuthorizationLevel.USER);

        l.info(user + " attempt signup");

        // Common enforcement checks
        checkValidUserID(user._id);
        _userManagement.checkUserIdDoesNotExist(user._id);

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _db.addUser(user, true);
    }

    @Override
    public ListenableFuture<Void> signUp(String userId, ByteString credentials, String firstName,
            String lastName, String orgId)
            throws ExNotFound, SQLException, ExNoPerm, ExAlreadyExist, IOException
    {
        // FIXME: ideally, all of this has to be in the same DB transaction

        Organization org = _db.getOrganization(orgId);
        if (!org.domainMatches(userId)) {
            throw new ExNoPerm("Email domain does not match " + org._allowedDomain);
        }

        signUpCommon(userId, credentials, firstName, lastName, orgId);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signUpWithBatch(String batchSignUpCode, String userId,
            ByteString credentials, String firstName, String lastName)
            throws SQLException, ExNotFound, ExAlreadyExist, IOException
    {
        // FIXME: ideally, all of this has to be in the same DB transaction

        String orgId = _db.checkBatchSignUpAndGetOrg(batchSignUpCode);
        if (orgId == null) {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }

        signUpCommon(userId, credentials, firstName, lastName, orgId);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String user_email)
            throws Exception
    {
        _userManagement.sendPasswordResetEmail(user_email);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resetPassword(String password_reset_token,
            ByteString new_credentials)
        throws Exception
    {
        _userManagement.resetPassword(password_reset_token,new_credentials);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> changePassword(ByteString old_credentials,
            ByteString new_credentials)
            throws Exception
    {
        _userManagement.changePassword(_sessionUser.getUser(),old_credentials,new_credentials);
        return createVoidReply();
    }
    @Override
    public ListenableFuture<Void> signUpWithTargeted(String targetedInvite, ByteString credentials,
            String firstName, String lastName)
            throws SQLException, ExAlreadyExist, IOException, ExNotFound
    {
        // FIXME: ideally, all of this has to be in the same DB transaction
        l.info("targeted sign-up: " + targetedInvite);

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
        crl = _db.getCRL();

        GetCRLReply reply = GetCRLReply.newBuilder()
                .addAllSerial(crl)
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> revokeDeviceCertificate(final ByteString deviceId)
        throws Exception
    {
        AbstractDatabaseTransaction<ListenableFuture<Void>> trans = new
                AbstractDatabaseTransaction<ListenableFuture<Void>>(_db)
        {
            // FIXME: remove the trans parameter and have it commit by itself.
            @Override
            protected ListenableFuture<Void> impl_(AbstractDatabase db,
                    AbstractDatabaseTransaction<ListenableFuture<Void>> trans)
                    throws Exception
            {
                SPDatabase spdb = (SPDatabase) db;
                String user = User.normalizeUserId(_sessionUser.getUser());
                DID did = new DID(deviceId);

                DeviceRow dr = spdb.getDevice(did);
                if (dr == null) {
                    throw new ExNotFound("Cannot revoke cert for non-existing device: " + did);
                } else if (!dr.getOwnerID().equals(user)) {
                    throw new ExNoPerm("Cannot revoke cert for device by a different owner: " +
                            user + " != " + dr.getOwnerID());
                }

                ImmutableList<Long> serials = spdb.revokeDeviceCertificate(did);

                // Push revoked serials to verkehr.
                updateCRL_(serials);

                // TODO (MP) update security epochs using serial list.
                // TODO (MP) publish updated epochs to verkehr.

                trans.commit_();
                return createVoidReply();
            }
        };

        return trans.run_();
    }

    @Override
    public ListenableFuture<Void> revokeUserCertificates()
        throws Exception
    {
        AbstractDatabaseTransaction<ListenableFuture<Void>> trans = new
                AbstractDatabaseTransaction<ListenableFuture<Void>>(_db)
        {
            @Override
            protected ListenableFuture<Void> impl_(AbstractDatabase db,
                    AbstractDatabaseTransaction<ListenableFuture<Void>> trans)
                    throws Exception
            {
                SPDatabase spdb = (SPDatabase) db;
                String user = User.normalizeUserId(_sessionUser.getUser());
                ImmutableList<Long> serials = spdb.revokeUserCertificates(user);

                // Push revoked serials to verkehr.
                updateCRL_(serials);

                // TODO (MP) update security epochs using serial list.
                // TODO (MP) publish updated epochs to verkehr.

                trans.commit_();
                return createVoidReply();
            }
        };

        return trans.run_();
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
        String user = User.normalizeUserId(_sessionUser.getUser());
        HashSet<String> sharedUsers = _db.getSharedUsersSet(user);

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
