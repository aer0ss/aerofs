package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolder.Factory;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.SharedFolderDatabase.GetACLResult;
import com.aerofs.sp.server.lib.ThreadLocalCertificateAuthenticator;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificateGenerationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.SPDatabase.DeviceInfo;
import com.aerofs.sp.server.lib.organization.Organization.UsersAndQueryCount;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
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
import com.aerofs.proto.Sp.ListSharedFoldersReply;
import com.aerofs.proto.Sp.ListSharedFoldersReply.PBSharedFolder;
import com.aerofs.proto.Sp.ListUsersReply;
import com.aerofs.proto.Sp.PBACLNotification;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.proto.Sp.ResolveTargetedSignUpCodeReply;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.aerofs.sp.server.SPParam.SP_EMAIL_NAME;

public class SPService implements ISPService
{
    private static final Logger l = Util.l(SPService.class);

    // the temporary user or device name used before SetPreferences is called
    private static final String UNKNOWN_DEVICE_NAME = "(unknown)";

    // TODO (WW) remove dependency to these database objects
    private final SPDatabase _db;
    private final SharedFolderDatabase _sfdb;
    private final CertificateDatabase _certdb;
    private final EmailSubscriptionDatabase _esdb;

    private final IThreadLocalTransaction<SQLException> _transaction;

    private VerkehrPublisher _verkehrPublisher;
    private VerkehrAdmin _verkehrAdmin;

    // Several methods in this SPService require access to the HttpSession's user id.
    // Since the Protobuf plugin cannot get access to the session user,
    // we use this interface to gain access to the user Id of the current SPServlet thread.
    // _sessionUser.get() returns the userId associated with the current HttpSession.
    // Note that the session is set externally in SPServlet.
    private final ISessionUser _sessionUser;

    private final PasswordManagement _passwordManagement;
    private final ThreadLocalCertificateAuthenticator _certificateAuthenticator;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final Device.Factory _factDevice;
    private final Certificate.Factory _factCert;
    private final SharedFolder.Factory _factSharedFolder;
    private final SharedFolderInvitation.Factory _factSFI;
    private final InvitationEmailer.Factory _factEmailer;

    SPService(SPDatabase db, SharedFolderDatabase sfdb,
            IThreadLocalTransaction<SQLException> transaction, ISessionUser sessionUser,
            PasswordManagement passwordManagement,
            ThreadLocalCertificateAuthenticator certificateAuthenticator, User.Factory factUser,
            Organization.Factory factOrg, OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice, Certificate.Factory factCert, CertificateDatabase certdb,
            EmailSubscriptionDatabase esdb, Factory factSharedFolder,
            SharedFolderInvitation.Factory factSFI, InvitationEmailer.Factory factEmailer)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _sfdb = sfdb;
        _certdb = certdb;

        _transaction = transaction;
        _sessionUser = sessionUser;
        _passwordManagement = passwordManagement;
        _certificateAuthenticator = certificateAuthenticator;
        _factUser = factUser;
        _factOrg = factOrg;
        _factOrgInvite = factOrgInvite;
        _factDevice = factDevice;
        _factCert = factCert;
        _esdb = esdb;
        _factSharedFolder = factSharedFolder;
        _factSFI = factSFI;
        _factEmailer = factEmailer;
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
        String user;
        try {
            user = _sessionUser.exists() ? _sessionUser.get().id().toString() : "user unknown";
        } catch (ExNoPerm enp) {
            throw SystemUtil.fatalWithReturn(enp);
        }

        l.warn(user + ": " + Util.e(e, ExNoPerm.class, ExBadCredential.class, ExBadArgs.class,
                ExAlreadyExist.class, ExNotFound.class));

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
        UsersAndQueryCount listAndCount = org.listUsers(search, maxResults, offset);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(users2PBUserLists(listAndCount._users))
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

        UsersAndQueryCount listAndCount = org.listUsersAuth(search, level, maxResults, offset);

        ListUsersReply reply = ListUsersReply.newBuilder()
                .addAllUsers(users2PBUserLists(listAndCount._users))
                .setFilteredCount(listAndCount._count)
                .setTotalCount(org.totalUserCount(level))
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    private static List<PBUser> users2PBUserLists(Collection<User> users)
            throws SQLException, ExNotFound
    {
        List<PBUser> pbusers = Lists.newArrayListWithCapacity(users.size());
        for (User user : users) {
            FullName fn = user.getFullName();
            pbusers.add(PBUser.newBuilder()
                    .setUserEmail(user.id().toString())
                    .setFirstName(fn._first)
                    .setLastName(fn._last)
                    .build());
        }
        return pbusers;
    }

    @Override
    public ListenableFuture<ListSharedFoldersReply> listSharedFolders(Integer maxResults,
            Integer offset)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        user.throwIfNotAdmin();
        Organization org = user.getOrganization();

        int sharedFolderCount = org.countSharedFolders();
        Collection<SharedFolder> sfs = org.listSharedFolders(sanitizeMaxResults(maxResults),
                sanitizeOffset(offset));

        List<PBSharedFolder> pbs = Lists.newArrayListWithCapacity(sfs.size());
        for (SharedFolder sf : sfs) {
            pbs.add(PBSharedFolder.newBuilder()
                    .setStoreId(sf.id().toPB())
                    .setName(sf.getName())
                    .addAllSubjectRole(getACL(sf))
                    .build());
        }

        _transaction.commit();

        return createReply(ListSharedFoldersReply.newBuilder()
                .addAllSharedFolders(pbs)
                .setTotalCount(sharedFolderCount)
                .build());
    }

    private List<PBSubjectRolePair> getACL(SharedFolder sf)
            throws SQLException
    {
        Collection<User> sfusers = sf.getUsers();
        List<PBSubjectRolePair> pbsrps = Lists.newArrayListWithCapacity(sfusers.size());
        for (User sfuser : sfusers) {
            // skip team server ids.
            // TODO (WW) should we move it to SharedFolderDatabase.getUsers()?
            if (sfuser.id().isTeamServerID()) continue;
            Role role = sf.getRoleNullable(sfuser);
            assert role != null;
            pbsrps.add(PBSubjectRolePair.newBuilder()
                    .setSubject(sfuser.id().toString())
                    .setRole(role.toPB())
                    .build());
        }
        return pbsrps;
    }

    private int sanitizeOffset(Integer offset)
    {
        return offset == null || offset < 0 ? 0 : offset;
    }

    private static int sanitizeMaxResults(Integer maxResults)
    {
        // TODO (WW) force the client to always provide a max result. after all determine the max
        // result is UI's responsibility.
        final int DEFAULT_MAX_RESULTS = 100;
        // To avoid DoS attacks forbid listSharedFolders queries exceeding 1000 returned results
        final int ABSOLUTE_MAX_RESULTS = 1000;

        if (maxResults == null || maxResults < 0) return DEFAULT_MAX_RESULTS;
        else if (maxResults > ABSOLUTE_MAX_RESULTS) return ABSOLUTE_MAX_RESULTS;
        else return maxResults;
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

        if (!requester.getLevel().covers(AuthorizationLevel.ADMIN) ||
                // requester's level must cover the new level
                !requester.getLevel().covers(newAuth)) {
            throw new ExNoPerm("you have no permissions to change authorization");
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
        // send verkehr notification as the last step of the transaction
        publish_(_sessionUser.get().addAndMoveToOrganization(orgName));
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetOrganizationInvitationsReply> getOrganizationInvitations()
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();

        List<OrganizationInvitation> invitations = user.getOrganizationInvitations();

        List<GetOrganizationInvitationsReply.OrganizationInvitation> invitationsWireable =
                Lists.newArrayList();

        for (OrganizationInvitation invite :invitations) {
            GetOrganizationInvitationsReply.OrganizationInvitation.Builder builder =
                    GetOrganizationInvitationsReply.OrganizationInvitation.newBuilder();

            builder.setInviter(invite.getInviter().id().toString());
            builder.setOrganizationName(invite.getOrganization().getName());
            builder.setOrganizationId(invite.getOrganization().id().getInt());

            invitationsWireable.add(builder.build());
        }

        GetOrganizationInvitationsReply reply = GetOrganizationInvitationsReply.newBuilder()
                .addAllOrganizationInvitations(invitationsWireable)
                .build();

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<GetOrgPreferencesReply> getOrgPreferences()
        throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        Organization org = user.getOrganization();

        GetOrgPreferencesReply orgPreferences = GetOrgPreferencesReply.newBuilder()
                .setOrganizationName(org.getName())
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
        String email = _esdb.getEmail(unsubscribeToken);
        _esdb.removeEmailSubscription(unsubscribeToken);
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
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();

        Map<UserID, Long> epochs;

        // Create the organzation if necessary
        if (user.getOrganization().isDefault()) {
            epochs = user.addAndMoveToOrganization("An Awesome Team");
        } else if (!user.getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm();
        } else {
            epochs = Collections.emptyMap();
        }

        UserID tsUserID = user.getOrganization().id().toTeamServerUserID();

        GetTeamServerUserIDReply reply = GetTeamServerUserIDReply.newBuilder()
                .setId(tsUserID.toString())
                .build();

        // send verkehr notification as the last step of the transaction
        publish_(epochs);

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

        User tsUser = _factUser.create(user.getOrganization().id().toTeamServerUserID());

        // Certify device
        Device device = _factDevice.create(deviceId);
        device.createNewDevice(tsUser, UNKNOWN_DEVICE_NAME);
        CertifyDeviceReply reply = certifyDevice(csr, device);

        _transaction.commit();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<GetSharedFolderNamesReply> getSharedFolderNames(
            List<ByteString> shareIds)
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        List<String> names = Lists.newArrayListWithCapacity(shareIds.size());
        for (ByteString shareId : shareIds) {
            SharedFolder sf = _factSharedFolder.create(shareId);
            // throws ExNoPerm if the user doesn't have permission to view the name
            sf.getRoleThrows(user);
            names.add(sf.getName());
        }

        _transaction.commit();

        return createReply(GetSharedFolderNamesReply.newBuilder().addAllFolderName(names).build());
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
                .setCount(_sessionUser.get().getSignUpInvitationsQuota())
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
            device.createNewDevice(user, UNKNOWN_DEVICE_NAME);
        }

        CertifyDeviceReply reply = certifyDevice(csr, device);

        _transaction.commit();

        return createReply(reply);
    }

    private CertifyDeviceReply certifyDevice(ByteString csr, Device device)
            throws SignatureException, IOException, NoSuchAlgorithmException, ExBadArgs, ExNotFound,
            ExAlreadyExist, SQLException, CertificateException
    {
        CertificateGenerationResult cert = device.certify(new PKCS10(csr.toByteArray()));
        return CertifyDeviceReply.newBuilder()
                .setCert(cert.toString())
                .build();
    }


    @Override
    public ListenableFuture<Void> shareFolder(String folderName, ByteString shareId,
            List<PBSubjectRolePair> rolePairs, @Nullable String note)
            throws Exception
    {
        SharedFolder sf = _factSharedFolder.create(shareId);
        User sharer = _sessionUser.get();
        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(rolePairs);

        l.info(sharer + " shares " + sf + " with " + srps.size() + " users");
        if (srps.isEmpty()) throw new ExBadArgs("must specify one or more sharee");

        _transaction.begin();

        Map<UserID, Long> epochs = addSharedFolderIfNecessary(folderName, sf, sharer);

        List<InvitationEmailer> emailers = createFolderInvitationAndEmailer(folderName, note, sf,
                sharer, srps);

        // send verkehr notification as the last step of the transaction
        publish_(epochs);

        _transaction.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    private Map<UserID, Long> addSharedFolderIfNecessary(String folderName, SharedFolder sf,
            User sharer)
            throws ExNotFound, SQLException, ExNoPerm, IOException, ExAlreadyExist
    {
        // Only verified users can share
        if (!sharer.isVerified()) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm(sharer + " is not yet verified");
        }

        Map<UserID, Long> epochs;
        if (sf.exists()) {
            sf.throwIfNotOwnerAndNotAdmin(sharer);
            epochs = Collections.emptyMap();
        } else {
            // The store doesn't exist. Create it and add the user as the owner.
            epochs = sf.createNewSharedFolder(folderName, sharer);
        }
        return epochs;
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
        SharedFolderInvitation sfi = _factSFI.createWithGeneratedCode();
        sfi.createNewSharedFolderInvitation(sharer, sharee, sf, role, folderName);

        InvitationEmailer emailer;
        if (sharee.exists()) {
            // send foler invitation email
            emailer = _factEmailer.createFolderInvitationEmailer(sharer.id().toString(),
                    sharee.id().toString(), sharer.getFullName()._first, folderName, note,
                    sfi.code());
        } else {
            emailer = inviteToSignUp(sharee, sharer, folderName, note);
        }
        return emailer;
    }

    private InvitationEmailer inviteToSignUp(User invitee, User inviter,
            @Nullable String folderName, @Nullable String note)
            throws SQLException, IOException, ExNotFound
    {
        return inviteToSignUp(invitee, inviter, inviter.getFullName()._first, folderName, note);
    }

    /**
     * Call this method to use an inviter name from inviter.getFullName()._first
     */
    InvitationEmailer inviteToSignUp(User invitee, User inviter, String inviterName,
            @Nullable String folderName, @Nullable String note)
            throws SQLException, IOException
    {
        String code = invitee.addSignUpInvitationCode(inviter);

        _esdb.addEmailSubscription(invitee.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        return _factEmailer.createSignUpInvitationEmailer(inviter.id().toString(),
                invitee.id().toString(), inviterName, folderName, note, code);
    }

    @Override
    public ListenableFuture<Void> joinSharedFolder(String code) throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        SharedFolderInvitation sfi = _factSFI.create(code);

        l.info(user + " joins " + sfi);

        User sharee = sfi.getSharee();
        if (!user.equals(sharee)) {
            throw new ExNoPerm("your email " + user.id() + " does not match the expected " +
                    sharee.id());
        }

        // Because the folder code is valid and was received by email, the user is verified.
        user.setVerified();

        // create ACL entry for invitee
        Map<UserID, Long> epochs = sfi.getSharedFolder().addACL(sharee, sfi.getRole());

        // TODO: figure out when, if ever, it becomes safe to delete folder invitations
        //_db.removeFolderInvitation(code);

        // send verkehr notifications as the last step of the transaction
        publish_(epochs);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListPendingFolderInvitationsReply> listPendingFolderInvitations()
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();

        Collection<SharedFolderInvitation> sfis = _factSFI.listAll(user);

        // Throw ExNoPerm only if user isn't verified AND there are shared folder invitations to
        // accept.
        if (!sfis.isEmpty() && !user.isVerified()) {
            throw new ExNoPerm("email address not verified");
        }

        ListPendingFolderInvitationsReply.Builder builder =
                ListPendingFolderInvitationsReply.newBuilder();
        for (SharedFolderInvitation sfi : sfis) {
            builder.addInvitations(PBFolderInvitation.newBuilder()
                    .setSharedFolderCode(sfi.code())
                    .setFolderName(sfi.getFolderName())
                    .setSharer(sfi.getSharer().id().toString()));
        }

        _transaction.commit();

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
        UserID result = _db.getSignUpInvitation(tsc);
        _transaction.commit();

        return createReply(ResolveTargetedSignUpCodeReply.newBuilder()
                .setEmailAddress(result.toString())
                .build());
    }

    @Override
    public ListenableFuture<Void> inviteUser(List<String> userIdStrings)
            throws SQLException, ExBadArgs, ExAlreadyExist, ExEmailSendingFailed,
            ExNotFound, IOException, ExNoPerm
    {
        if (userIdStrings.isEmpty()) {
            throw new ExBadArgs("Must specify one or more invitees");
        }

        _transaction.begin();

        User inviter = _sessionUser.get();
        l.info("invite " + userIdStrings.size() + " users by " + inviter);

        if (!inviter.isAdmin()) enforceSignUpInvitationQuota(userIdStrings, inviter);

        // The sending of invitation emails is deferred to the end of the transaction to ensure
        // that all business logic checks pass and the changes are sucessfully committed to the DB
        List<InvitationEmailer> emailers = Lists.newLinkedList();
        for (String inviteeString : userIdStrings) {
            User invitee = _factUser.createFromExternalID(inviteeString);

            if (invitee.exists()) {
                throw new ExAlreadyExist("user already exists (" + invitee + ")");
            }

            emailers.add(inviteToSignUp(invitee, inviter, null, null));
        }

        _transaction.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    private void enforceSignUpInvitationQuota(List<String> userIdStrings, User inviter)
            throws ExNotFound, SQLException, ExNoPerm
    {
        int left = inviter.getSignUpInvitationsQuota() - userIdStrings.size();
        if (left < 0) throw new ExNoPerm();

        inviter.setSignUpInvitationQuota(left);
    }

    @Override
    public ListenableFuture<Void> inviteToOrganization(String userIdString)
            throws SQLException, ExNoPerm, ExNotFound, IOException, ExEmailSendingFailed,
            ExAlreadyExist, ExAlreadyInvited
    {
        _transaction.begin();

        User inviter = _sessionUser.get();
        inviter.throwIfNotAdmin();

        User invitee = _factUser.create(UserID.fromExternal(userIdString));
        Organization org = inviter.getOrganization();

        l.info("Send organization invite by " + inviter + " to " + invitee);

        if (invitee.exists() && invitee.getOrganization().equals(org)) {
            l.warn(invitee + " already a member.");
            throw new ExAlreadyExist();
        }

        OrganizationInvitation invite = _factOrgInvite.create(invitee.id(), org.id());

        if (invite.exists()) {
            l.warn(invitee + " already invited.");
            throw new ExAlreadyInvited();
        }

        _factOrgInvite.createNewOrganizationInvitation(inviter.id(), invitee.id(), org.id());
        _factEmailer.createOrganizationInvitationEmailer(inviter, invitee, org).send();

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> acceptOrganizationInvitation(Integer organizationID)
            throws SQLException, ExNoPerm, ExNotFound, ExAlreadyExist
    {
        _transaction.begin();

        User accepter = _sessionUser.get();
        Organization organization = _factOrg.create(new OrganizationID(organizationID));
        l.info("Accept org invite by " + accepter);

        OrganizationInvitation invite = _factOrgInvite.create(accepter.id(), organization.id());

        // Check to see if the user was actually invited to this organization.
        if (invite.getInviter().id().toString().isEmpty()) {
            throw new ExNoPerm();
        }

        // Are we already part of the target organization?
        if (accepter.getOrganization().equals(organization)) {
            throw new ExAlreadyExist();
        }

        accepter.setOrganization(invite.getOrganization());

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm
    {
        _transaction.begin();
        // TODO (WW) refactor as mentinoed in _sfdb.getACL().
        GetACLResult result = _sfdb.getACL(epoch, _sessionUser.getID());
        _transaction.commit(); // commit right away to avoid holding read locks

        // this means that no acl changes have occurred
        if (result._epoch == epoch) {
            l.info("no updates - matching epoch:" + epoch);
            return createReply(GetACLReply.newBuilder().setEpoch(epoch).build());
        }

        GetACLReply.Builder getACLBuilder = GetACLReply.newBuilder();
        for (Map.Entry<SID, List<SubjectRolePair>> entry : result._sid2srps.entrySet()) {
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
        getACLBuilder.setEpoch(result._epoch);

        return createReply(getACLBuilder.build());
    }

    @Override
    public ListenableFuture<Void> updateACL(final ByteString storeId,
            final List<PBSubjectRolePair> subjectRoleList)
            throws Exception
    {
        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(storeId);

        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(subjectRoleList);

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        _transaction.begin();
        sf.throwIfNotOwnerAndNotAdmin(user);
        Map<UserID, Long> epochs = sf.updateACL(srps);
        // send verkehr notification as the last step of the transaction
        publish_(epochs);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(final ByteString storeId,
            final List<String> subjectList)
            throws Exception
    {
        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(storeId);

        List<UserID> subjects = UserID.fromExternal(subjectList);

        _transaction.begin();
        sf.throwIfNotOwnerAndNotAdmin(user);
        Map<UserID, Long> updatedEpochs = sf.deleteACL(subjects);
        // send verkehr notification as the last step of the transaction
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

        if (user.id().isTeamServerID()) {
            l.debug("TS login: " + user);

            // Team servers use certificates (in this case the passed credentials don't matter).
            if (!_certificateAuthenticator.isAuthenticated())
            {
                l.warn(user + " ts not authenticated");
                throw new ExBadCredential();
            }

            Certificate cert = _factCert.create(_certificateAuthenticator.getSerial());
            if (cert.isRevoked()) {
                l.warn(user + " ts cert revoked");
                throw new ExBadCredential();
            }
        } else {
            l.debug("User login: " + userIdString);

            // Regular users still use username/password credentials.
            user.signIn(SPParam.getShaedSP(credentials.toByteArray()));
        }

        _sessionUser.set(user);

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> signUp(String userIdString, ByteString credentials,
            String firstName, String lastName)
            throws ExNotFound, SQLException, ExAlreadyExist, ExBadArgs, IOException, ExNoPerm
    {
        if (!Util.isValidEmailAddress(userIdString)) throw new ExBadArgs("invalid email address");

        User user = _factUser.createFromExternalID(userIdString);
        FullName fullName = new FullName(firstName, lastName);
        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());

        _transaction.begin();

        user.createNewUser(shaedSP, fullName, _factOrg.getDefault());

        //unsubscribe user from the aerofs invitation reminder mailing list
        _esdb.removeEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> sendPasswordResetEmail(String userIdString)
            throws Exception
    {
        _transaction.begin();
        _passwordManagement.sendPasswordResetEmail(_factUser.createFromExternalID(userIdString));
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resetPassword(String password_reset_token,
            ByteString new_credentials)
        throws Exception
    {
        _transaction.begin();
        _passwordManagement.resetPassword(password_reset_token,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> changePassword(ByteString old_credentials,
            ByteString new_credentials)
            throws Exception
    {
        _transaction.begin();
        _passwordManagement.changePassword(_sessionUser.getID(),old_credentials,new_credentials);
        _transaction.commit();

        return createVoidReply();
    }
    @Override
    public ListenableFuture<Void> signUpWithTargeted(String targetedInvite, ByteString credentials,
            String firstName, String lastName)
            throws SQLException, ExAlreadyExist, ExNotFound, ExBadArgs, IOException, ExNoPerm
    {
        l.info("targeted sign-up: " + targetedInvite);

        byte[] shaedSP = SPParam.getShaedSP(credentials.toByteArray());
        FullName fullName = new FullName(firstName, lastName);

        _transaction.begin();

        UserID userID  = _db.getSignUpInvitation(targetedInvite);
        User user = _factUser.create(userID);

        // All new users start in the default organization.
        user.createNewUser(shaedSP, fullName, _factOrg.getDefault());

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
    public ListenableFuture<Void> revokeUserDeviceCertificate(final ByteString deviceId)
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

        Certificate cert = device.getCertificate();
        cert.revoke();

        ImmutableList.Builder<Long> builder = ImmutableList.builder();
        builder.add(cert.serial());

        // Push revoked serials to verkehr.
        updateCRL_(builder.build());

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> revokeAllUserDeviceCertificates()
        throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();

        ImmutableList<Device> userDevices = user.getDevices();
        ImmutableList.Builder<Long> serials = ImmutableList.builder();

        for (Device device : userDevices) {
            Certificate cert = device.getCertificate();

            cert.revoke();
            serials.add(cert.serial());
        }

        // Push revoked serials to verkehr.
        updateCRL_(serials.build());

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> revokeTeamServerDeviceCertificate(final ByteString deviceId)
    {
        // TODO (MP) finish this... when finished enable shouldNotAllowTeamServerLoginWithRevokedCertificate.

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> revokeAllTeamServerDeviceCertificates()
    {
        // TODO (MP) finish this... when finished enable shouldNotAllowTeamServerLoginWithRevokedCertificate.

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
