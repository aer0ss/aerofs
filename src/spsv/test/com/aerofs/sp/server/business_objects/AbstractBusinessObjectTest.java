/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.lib.FullName;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.cert.Certificate;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;

abstract class AbstractBusinessObjectTest extends AbstractAutoTransactionedTestWithSPDatabase
{
    @Spy protected final SPDatabase db = new SPDatabase(sqlTrans);
    @Spy protected final OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
    @Spy protected final UserDatabase udb = new UserDatabase(sqlTrans);
    @Spy protected final SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
    @Spy protected final EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(sqlTrans);
    @Spy protected final OrganizationInvitationDatabase oidb =
            new OrganizationInvitationDatabase(sqlTrans);

    @Spy protected final DeviceDatabase ddb = new DeviceDatabase(sqlTrans);
    @Spy protected final CertificateDatabase cdb = new CertificateDatabase(sqlTrans);
    @Spy protected final CertificateGenerator cgen = new CertificateGenerator();
    @Spy protected final Certificate.Factory factCert = new Certificate.Factory(cdb);

    @Spy protected final Organization.Factory factOrg = new Organization.Factory();
    @Spy protected final SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
    @Spy protected final Device.Factory factDevice = new Device.Factory();
    @Spy protected final OrganizationInvitation.Factory factOrgInvite =
            new OrganizationInvitation.Factory();

    @Spy protected final User.Factory factUser = new User.Factory(udb, oidb, factDevice, factOrg,
            factOrgInvite, factSharedFolder);
    {
        factOrg.inject(odb, oidb, factUser, factSharedFolder, factOrgInvite);
        factSharedFolder.inject(sfdb, factUser);
        factDevice.inject(ddb, cdb, cgen, factUser, factCert);
    }

    private int nextUserID = 123;

    protected User newUser()
    {
        return newUser("user" + Integer.toString(nextUserID++) + "@email");
    }

    protected User newUser(String id)
    {
        return newUser(UserID.fromInternal(id));
    }

    protected User newUser(UserID id)
    {
        return factUser.create(id);
    }

    protected void saveUser(User user)
            throws Exception
    {
        user.save(new byte[0], new FullName("first", "last"));
    }

    protected User saveUser()
            throws Exception
    {
        User user = newUser();
        saveUser(user);
        return user;
    }

    private int nextOrganizationID = 123;

    protected Organization newOrganization()
    {
        return factOrg.create(new OrganizationID(nextOrganizationID++));
    }

    protected Organization saveOrganization()
            throws Exception
    {
        return factOrg.save();
    }

    SharedFolder newSharedFolder()
    {
        return newSharedFolder(SID.generate());
    }

    SharedFolder newSharedFolder(SID sid)
    {
        return factSharedFolder.create(sid);
    }

    SharedFolder saveSharedFolder(User owner)
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        return saveSharedFolder(SID.generate(), owner);
    }

    SharedFolder saveSharedFolder(SID sid, User owner)
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = factSharedFolder.create(sid);
        sf.save("shared folder", owner);
        return sf;
    }
}
