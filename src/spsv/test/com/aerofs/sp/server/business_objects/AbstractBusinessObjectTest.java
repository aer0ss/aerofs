/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
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
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;

abstract class AbstractBusinessObjectTest extends AbstractAutoTransactionedTestWithSPDatabase
{
    @Spy protected final SPDatabase db = new SPDatabase(trans);
    @Spy protected final OrganizationDatabase odb = new OrganizationDatabase(trans);
    @Spy protected final UserDatabase udb = new UserDatabase(trans);
    @Spy protected final SharedFolderDatabase sfdb = new SharedFolderDatabase(trans);
    @Spy protected final EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(trans);
    @Spy protected final OrganizationInvitationDatabase oidb =
            new OrganizationInvitationDatabase(trans);

    @Spy protected final Organization.Factory factOrg = new Organization.Factory();
    @Spy protected final SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
    @Spy protected final Device.Factory factDevice = new Device.Factory();
    @Spy protected final OrganizationInvitation.Factory factOrgInvite =
            new OrganizationInvitation.Factory();

    @Spy protected final User.Factory factUser = new User.Factory(udb, oidb, factDevice, factOrg,
            factOrgInvite, factSharedFolder);
    {
        factOrg.inject(odb, factUser, factSharedFolder);
        factSharedFolder.inject(sfdb, factUser);
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

    protected void saveUser(User user, Organization org)
            throws IOException, SQLException, ExAlreadyExist, ExNoPerm
    {
        user.save(new byte[0], new FullName("first", "last"), org);
    }

    private int nextOrganizationID = 123;

    protected Organization newOrganization()
    {
        return factOrg.create(new OrganizationID(nextOrganizationID++));
    }

    protected Organization saveOrganization()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        return factOrg.save("test org");
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
