package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBFolderInvitation;
import com.aerofs.proto.Sp.GetAuthorizationLevelReply;
import com.aerofs.proto.Sp.GetOrganizationInvitationsReply;
import com.aerofs.proto.Sp.GetTeamServerUserIDReply;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.email.DeviceCertifiedEmailer;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolder.Factory;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificateGenerationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.SPDatabase.DeviceInfo;
import com.aerofs.sp.server.lib.organization.Organization.UsersAndQueryCount;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.CertificateAuthenticator;
import com.aerofs.sp.server.lib.user.User.PendingSharedFolder;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
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
import com.aerofs.proto.Sp.ListSharedFoldersReply;
import com.aerofs.proto.Sp.ListSharedFoldersReply.PBSharedFolder;
import com.aerofs.proto.Sp.ListUsersReply;
import com.aerofs.proto.SpNotifications.PBACLNotification;
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
import com.google.common.collect.Maps;
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

public class SPService implements ISPService
{
    private static final Logger l = Util.l(SPService.class);

    // the temporary user or device name used before SetPreferences is called
    private static final String UNKNOWN_DEVICE_NAME = "(unknown)";

    // TODO (WW) remove dependency to these database objects
    private final SPDatabase _db;
    private final CertificateDatabase _certdb;
    private final EmailSubscriptionDatabase _esdb;

    private final IThreadLocalTransaction<SQLException> _transaction;

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
    private final DeviceCertifiedEmailer _deviceCertifiedEmailer;
    private final CertificateAuthenticator _certificateAuthenticator;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final Device.Factory _factDevice;
    private final Certificate.Factory _factCert;
    private final SharedFolder.Factory _factSharedFolder;
    private final InvitationEmailer.Factory _factEmailer;

    SPService(SPDatabase db, IThreadLocalTransaction<SQLException> transaction,
            ISessionUser sessionUser, PasswordManagement passwordManagement,
            CertificateAuthenticator certificateAuthenticator, User.Factory factUser,
            Organization.Factory factOrg, OrganizationInvitation.Factory factOrgInvite,
            Device.Factory factDevice, Certificate.Factory factCert, CertificateDatabase certdb,
            EmailSubscriptionDatabase esdb, Factory factSharedFolder,
            InvitationEmailer.Factory factEmailer, DeviceCertifiedEmailer deviceCertifiedEmailer)
    {
        // FIXME: _db shouldn't be accessible here; in fact you should only have a transaction
        // factory that gives you transactions....
        _db = db;
        _certdb = certdb;

        _transaction = transaction;
        _sessionUser = sessionUser;
        _passwordManagement = passwordManagement;
        _deviceCertifiedEmailer = deviceCertifiedEmailer;
        _certificateAuthenticator = certificateAuthenticator;
        _factUser = factUser;
        _factOrg = factOrg;
        _factOrgInvite = factOrgInvite;
        _factDevice = factDevice;
        _factCert = factCert;
        _esdb = esdb;
        _factSharedFolder = factSharedFolder;
        _factEmailer = factEmailer;
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
            user = _sessionUser.exists() ? _sessionUser.get().id().toString() : "user unknown";
        } catch (ExNoPerm enp) {
            throw SystemUtil.fatalWithReturn(enp);
        }

        l.warn(user + ": " + Util.e(e, ExNoPerm.class, ExBadCredential.class, ExBadArgs.class,
                ExAlreadyExist.class, ExNotFound.class));

        // Notify SPTransaction that an exception occurred.
        _transaction.handleException();

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

        l.info("Set auth requester=" + requester.id() + " subject=" + subject.id() + " auth=" +
                newAuth);

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
        _sessionInvalidator.invalidate(subject.id());

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> addOrganization(final String orgName)
            throws Exception
    {
        User user = _sessionUser.get();

        _transaction.begin();
        Set<UserID> users = user.addAndMoveToOrganization(orgName);
        // send verkehr notification as the last step of the transaction
        publish_(incrementACLEpochs_(users));
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

        Set<UserID> users;

        // Create the organzation if necessary
        if (user.getOrganization().isDefault()) {
            users = user.addAndMoveToOrganization("An Awesome Team");
        } else if (!user.getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm();
        } else {
            users = Collections.emptySet();
        }

        UserID tsUserID = user.getOrganization().id().toTeamServerUserID();

        GetTeamServerUserIDReply reply = GetTeamServerUserIDReply.newBuilder()
                .setId(tsUserID.toString())
                .build();

        // send verkehr notification as the last step of the transaction
        publish_(incrementACLEpochs_(users));

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
        device.save(tsUser, UNKNOWN_DEVICE_NAME);
        CertifyDeviceReply reply = certifyDevice(csr, device);

        _deviceCertifiedEmailer.sendTeamServerDeviceCertifiedEmail(_sessionUser.get());
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

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME,
                _sessionUser.get().id().toString(), null, UserID.fromExternal(userId).toString(),
                body, null, true, null);

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
            device.save(user, UNKNOWN_DEVICE_NAME);
        }

        CertifyDeviceReply reply = certifyDevice(csr, device);

        // Do not send email notification when we are recertifying.
        if (!recertify) {
            _deviceCertifiedEmailer.sendDeviceCertifiedEmail(_sessionUser.get());
        }

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
        if (sf.id().isRoot()) throw new ExBadArgs("Cannot share root");

        User sharer = _sessionUser.get();
        List<SubjectRolePair> srps = SubjectRolePairs.listFromPB(rolePairs);

        l.info(sharer + " shares " + sf + " with " + srps.size() + " users");
        if (srps.isEmpty()) throw new ExBadArgs("must specify one or more sharee");

        _transaction.begin();

        Set<UserID> users = addSharedFolderIfNecessary(folderName, sf, sharer);

        List<InvitationEmailer> emailers = createFolderInvitationAndEmailer(folderName, note, sf,
                sharer, srps);

        // send verkehr notification as the last step of the transaction
        publish_(incrementACLEpochs_(users));

        _transaction.commit();

        for (InvitationEmailer emailer : emailers) emailer.send();

        return createVoidReply();
    }

    private Set<UserID> addSharedFolderIfNecessary(String folderName, SharedFolder sf, User sharer)
            throws ExNotFound, SQLException, ExNoPerm, IOException, ExAlreadyExist
    {
        // Only verified users can share
        if (!sharer.isVerified()) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm(sharer + " is not yet verified");
        }

        if (sf.exists()) {
            sf.throwIfNotOwnerAndNotAdmin(sharer);
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
        // add ACL entry w/ pending bit
        // TODO: silently ignore already invited users instead of throwing?
        sf.addPendingACL(sharer, sharee, role);

        InvitationEmailer emailer;
        if (sharee.exists()) {
            // send folder invitation email
            emailer = _factEmailer.createFolderInvitationEmailer(sharer.id().toString(),
                    sharee.id().toString(), sharer.getFullName()._first, folderName, note, sf.id());
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
        assert !invitee.exists();

        String code = invitee.addSignUpInvitationCode(inviter);

        _esdb.insertEmailSubscription(invitee.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        return _factEmailer.createSignUpInvitationEmailer(inviter.id().toString(),
                invitee.id().toString(), inviterName, folderName, note, code);
    }

    @Override
    public ListenableFuture<Void> joinSharedFolder(ByteString sid) throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " joins " + sf);

        if (!sf.exists()) throw new ExNotFound("No such shared folder");

        if (sf.isMember(user)) {
            throw new ExAlreadyExist("You are already a member of this shared folder");
        }

        if (!sf.isInvited(user)) {
            throw new ExNoPerm("Your have not been invited to this shared folder");
        }

        // Because the folder code is valid and was received by email, the user is verified.
        // TODO: can we still assume the user to be verified by virtue of joining a shared folder?
        user.setVerified();

        // reset pending bit to make user a member of the shared folder
        Set<UserID> users = sf.resetPending(user);

        // send verkehr notifications as the last step of the transaction
        publish_(incrementACLEpochs_(users));

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> ignoreSharedFolderInvitation(ByteString sid) throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " ignore " + sf);

        if (!sf.exists()) {
            throw new ExNotFound("No such shared folder");
        }
        if (sf.isMember(user)) {
            throw new ExAlreadyExist("You have already accepted this invitation");
        }
        if (!sf.isInvited(user)) {
            throw new ExNoPerm("You have not been invited to this shared folder");
        }

        // Ignore the invitation by deleting the ACL.
        try {
            sf.deleteACL(Collections.singleton(user.id()));
        } catch (ExNoPerm e) {
            // we should be able to ignore an invitation even if the shared folder somehow lost
            // all its owners...
            l.debug("owner-less folder " + sf);
        }

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> leaveSharedFolder(ByteString sid) throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();
        SharedFolder sf = _factSharedFolder.create(new SID(sid));

        l.info(user + " leave " + sf);

        if (!sf.exists()) throw new ExNotFound("No such shared folder");

        if (sf.id().isRoot()) throw new ExBadArgs("Cannot leave root folder");

        // silently ignore leave call from pending users
        if (!sf.isInvited(user)) {
            if (!sf.isMember(user)) {
                throw new ExNotFound("You are not a member of this shared folder");
            }

            // set pending bit
            Set<UserID> users = sf.setPending(user);

            // send verkehr notifications as the last step of the transaction
            publish_(incrementACLEpochs_(users));
        }

        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListPendingFolderInvitationsReply> listPendingFolderInvitations()
            throws Exception
    {
        _transaction.begin();

        User user = _sessionUser.get();

        Collection<PendingSharedFolder> psfs = user.getPendingSharedFolders();

        // Throw ExNoPerm only if user isn't verified AND there are shared folder invitations to
        // accept.
        if (!psfs.isEmpty() && !user.isVerified()) {
            throw new ExNoPerm("email address not verified");
        }

        ListPendingFolderInvitationsReply.Builder builder =
                ListPendingFolderInvitationsReply.newBuilder();
        for (PendingSharedFolder psf : psfs) {
            builder.addInvitation(PBFolderInvitation.newBuilder()
                    .setShareId(psf._sf.id().toPB())
                    .setFolderName(psf._sf.getName())
                    .setSharer(psf._sharer.toString()));
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
    public ListenableFuture<Void> inviteToSignUp(List<String> userIdStrings)
            throws SQLException, ExBadArgs, ExEmailSendingFailed, ExNotFound, IOException, ExNoPerm
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
                l.info(inviter + " invites " + invitee + ": already exists. skip.");
            } else {
                emailers.add(inviteToSignUp(invitee, inviter, null, null));
            }
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

        _factOrgInvite.save(inviter.id(), invitee.id(), org.id());
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
        if (!invite.exists()) {
            throw new ExNotFound();
        }

        // Are we already part of the target organization?
        if (accepter.getOrganization().equals(organization)) {
            throw new ExAlreadyExist();
        }

        accepter.setOrganization(invite.getOrganization());
        accepter.setLevel(AuthorizationLevel.USER);

        invite.delete();
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> ignoreOrganizationInvitation(Integer organizationID)
            throws SQLException, ExNoPerm, ExNotFound
    {
        _transaction.begin();

        User ignorer = _sessionUser.get();
        Organization organization = _factOrg.create(new OrganizationID(organizationID));
        l.info("Ignore org invite by " + ignorer);

        OrganizationInvitation invite = _factOrgInvite.create(ignorer.id(), organization.id());

        invite.delete();
        _transaction.commit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(final Long epoch)
            throws SQLException, ExNoPerm
    {
        User user = _sessionUser.get();
        GetACLReply.Builder bd = GetACLReply.newBuilder();

        _transaction.begin();

        long userEpoch = user.getACLEpoch();
        if (userEpoch == epoch) {
            l.info("no updates - matching epoch:" + epoch);
        } else {
            for (SharedFolder sf : user.getSharedFolders()) {
                l.info("add s:" + sf.id());
                PBStoreACL.Builder aclBuilder = PBStoreACL.newBuilder();
                aclBuilder.setStoreId(sf.id().toPB());
                for (SubjectRolePair srp : sf.getACL()) {
                    l.info("add j:" + srp._subject + " r:" + srp._role.getDescription());
                    aclBuilder.addSubjectRole(srp.toPB());
                }
                bd.addStoreAcl(aclBuilder);
            }
        }
        _transaction.commit();

        bd.setEpoch(userEpoch);
        return createReply(bd.build());
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
        Set<UserID> users = sf.updateACL(srps);
        // send verkehr notification as the last step of the transaction
        publish_(incrementACLEpochs_(users));
        _transaction.commit();

        return createVoidReply();
    }

    private Map<UserID, Long> incrementACLEpochs_(Set<UserID> users) throws SQLException
    {
        Map<UserID, Long> m = Maps.newHashMap();
        for (UserID u : users) {
            m.put(u, _factUser.create(u).incrementACLEpoch());
        }
        return m;
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
        Set<UserID> users = sf.deleteACL(subjects);
        // send verkehr notification as the last step of the transaction
        publish_(incrementACLEpochs_(users));
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
            l.debug("TS sign in: " + user);

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
            l.debug("User sign in: " + userIdString);

            // Regular users still use username/password credentials.
            user.signIn(SPParam.getShaedSP(credentials.toByteArray()));
        }

        // Set the session cookie.
        _sessionUser.set(user);
        // Update the user tracker so we can invalidate sessions if needed.
        _userTracker.signIn(user.id(), _sessionUser.getSessionID());

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

        user.save(shaedSP, fullName, _factOrg.getDefault());

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
        _passwordManagement.changePassword(_sessionUser.get().id(), old_credentials,
                new_credentials);
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
        user.save(shaedSP, fullName, _factOrg.getDefault());

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

        Set<UserID> sharedUsers = _db.getSharedUsersSet(_sessionUser.get().id());

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
