/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
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

    @Spy protected final Organization.Factory factOrg = new Organization.Factory();
    @Spy protected final SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
    @Spy protected final Device.Factory factDevice = new Device.Factory();
    @Spy protected final User.Factory factUser = new User.Factory(udb, factDevice, factOrg,
            factSharedFolder);
    {
        factOrg.inject(odb, factUser);
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

    protected void createNewUser(User user, Organization org)
            throws IOException, SQLException, ExAlreadyExist, ExNoPerm
    {
        user.createNewUser(new byte[0], new FullName("first", "last"), org);
    }

    private int nextOrgID = 123;

    protected Organization newOrg()
    {
        return factOrg.create(new OrgID(nextOrgID++));
    }

    protected Organization createNewOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException
    {
        return factOrg.createNewOrganization("test org");
    }

    SharedFolder newSharedFolder()
    {
        return newSharedFolder(SID.generate());
    }

    SharedFolder newSharedFolder(SID sid)
    {
        return factSharedFolder.create_(sid);
    }

    SharedFolder createNewSharedFolder(User owner)
            throws ExNoPerm, IOException, ExNotFound, ExAlreadyExist, SQLException
    {
        return createNewSharedFolder(SID.generate(), owner);
    }

    SharedFolder createNewSharedFolder(SID sid, User owner)
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        SharedFolder sf = factSharedFolder.create_(sid);
        sf.createNewSharedFolder("shared folder", owner);
        return sf;
    }
}
