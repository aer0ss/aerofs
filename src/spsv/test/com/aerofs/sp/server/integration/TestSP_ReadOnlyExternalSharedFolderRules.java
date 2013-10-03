/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Role;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningAddExternalUser;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.shared_folder_rules.ISharedFolderRules;
import com.aerofs.sp.server.shared_folder_rules.SharedFolderRulesFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_ReadOnlyExternalSharedFolderRules extends AbstractSPFolderTest
{
    static final String INTERNAL_ADDRESSES = "abc.*|.*xyz";

    // the user ids that match the white list
    User internalUser1 = factUser.create(UserID.fromInternal("abc@adf"));
    User internalUser2 = factUser.create(UserID.fromInternal("xocjaxyz"));
    User internalUser3 = factUser.create(UserID.fromInternal("xyz"));

    // the user ids that don't match the white list
    User externalUser1 = factUser.create(UserID.fromInternal("haabc@adf"));
    User externalUser2 = factUser.create(UserID.fromInternal("xyza"));
    User externalUser3 = factUser.create(UserID.fromInternal("xyzabc"));

    SID sid = SID.generate();
    // This user is an internal user
    User internalSharer = factUser.create(UserID.fromInternal("abc"));

    @Before
    public void setup()
            throws Exception
    {
        // Ask SharedFolderRulesFactory to create ReadOnlyExternalFolderRules
        setProperties(true, INTERNAL_ADDRESSES);

        ISharedFolderRules sharedFolderRules = SharedFolderRulesFactory.create(factUser);

        // reconstruct SP using the new shared folder rules
        service = new SPService(db, sqlTrans, jedisTrans, sessionUser, passwordManagement,
                certificateAuthenticator, factUser, factOrg, factOrgInvite, factDevice, certdb,
                esdb, factSharedFolder, factEmailer, _deviceRegistrationEmailer,
                _requestToSignUpEmailer, commandQueue, analytics, identitySessionManager,
                authenticator, sharedFolderRules);
        wireSPService();

        sqlTrans.begin();
        saveUser(internalSharer);
        sqlTrans.commit();
    }

    @After
    public void teardown()
    {
        // Reset the property so subsequent tests can construct SP with the default shared folder
        // rules.
        setProperties(false, "");
    }

    private void setProperties(boolean enableReadOnlyExternalFolderRules, String internalAddresses)
    {
        Properties props = new Properties();
        props.put("shared_folder_rules.readonly_external_folders", enableReadOnlyExternalFolderRules ? "true" : "false");
        props.put("internal_email_pattern", internalAddresses);
        ConfigurationProperties.setProperties(props);
    }

    @Test
    public void shouldAllowFolderCreatorAsInternalUserToAddInternalUsers()
            throws Exception
    {
        shareFolder(internalSharer, sid, internalUser1, Role.EDITOR);
        shareFolder(internalSharer, sid, internalUser2, Role.OWNER);
    }

    @Test
    public void shouldAllowSharerAsInternalUserToAddInternalUsers()
            throws Exception
    {
        // create the internal user
        sqlTrans.begin();
        saveUser(internalUser1);
        sqlTrans.commit();

        shareFolder(internalSharer, sid, internalUser1, Role.OWNER);
        joinSharedFolder(internalUser1, sid);

        shareFolder(internalUser1, sid, internalUser2, Role.OWNER);
        shareFolder(internalUser1, sid, internalUser3, Role.EDITOR);
    }

    // There should be no warnings or errors when external users invite either external or internal
    // users as viewers or owners
    @Test
    public void shouldAllowFolderCreatorAsExternalUserToAddViewersAndOwner()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        shareFolder(externalUser1, sid, internalUser2, Role.VIEWER);
        shareFolder(externalUser1, sid, externalUser3, Role.OWNER);
    }

    // There should be no warnings or errors when external users invite either external or internal
    // users as viewers or owners
    @Test
    public void shouldAllowSharerAsExternalUserToAddViewersAndOwner()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.OWNER);
        joinSharedFolder(externalUser1, sid);

        shareFolder(externalUser1, sid, externalUser2, Role.OWNER);
        shareFolder(externalUser1, sid, externalUser3, Role.VIEWER);
        shareFolder(externalUser1, sid, internalUser1, Role.OWNER);
        shareFolder(externalUser1, sid, internalUser2, Role.VIEWER);
    }

    @Test
    public void shouldDisallowFolderCreaterAsExternalUserToAddEditors()
            throws Exception
    {
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        try {
            shareFolder(externalUser1, sid, internalUser1, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {}
    }

    @Test
    public void shouldDisallowSharerAsExternalUserToAddEditors()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.OWNER);
        joinSharedFolder(externalUser1, sid);

        try {
            shareFolder(externalUser1, sid, externalUser2, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }

        try {
            shareFolder(externalUser1, sid, internalUser1, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldDisallowFolderCreaterAsInternalUserToAddExternalEditors()
            throws Exception
    {
        try {
            shareFolder(internalSharer, sid, externalUser1, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {}
    }

    @Test
    public void shouldDisallowSharerAsInternalUserToAddExternalEditors()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(internalUser1);
        sqlTrans.commit();

        shareFolder(internalSharer, sid, internalUser1, Role.OWNER);
        joinSharedFolder(internalUser1, sid);

        try {
            shareFolder(internalUser1, sid, externalUser2, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldWarnWhenAddingExternalUsers()
            throws Exception
    {
        // create an internal folder
        shareFolder(internalSharer, sid, internalUser1, Role.EDITOR);

        // attempt to add the first external user
        try {
            shareFolder(internalSharer, sid, externalUser1, Role.VIEWER);
            fail();
        } catch (ExSharedFolderRulesWarningAddExternalUser e) {
            sqlTrans.rollback();
        }

        // add the first external user
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        // attempt to add the second external user
        try {
            shareFolder(internalSharer, sid, externalUser2, Role.VIEWER);
            fail();
        } catch (ExSharedFolderRulesWarningAddExternalUser e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldConvertEditorsToViewersWhenConvertingInternalFolderToExternal()
            throws Exception
    {
        // create an internal folder
        shareFolder(internalSharer, sid, internalUser1, Role.EDITOR);
        shareFolder(internalSharer, sid, internalUser2, Role.EDITOR);
        SharedFolder sf = factSharedFolder.create(sid);

        // make sure both users are an editor
        sqlTrans.begin();
        assertEquals(sf.getRole(internalUser1), Role.EDITOR);
        assertEquals(sf.getRole(internalUser2), Role.EDITOR);
        sqlTrans.commit();

        // convert the folder to an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        // make sure both users are converted to viewers
        sqlTrans.begin();
        assertEquals(sf.getRole(internalUser1), Role.VIEWER);
        assertEquals(sf.getRole(internalUser2), Role.VIEWER);
        sqlTrans.commit();
    }

    @Test
    public void shouldDisallowAddingEditorInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        try {
            shareFolder(internalSharer, sid, internalUser3, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }

        try {
            shareFolder(internalSharer, sid, externalUser2, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {}
    }

    @Test
    public void shouldDisallowSettingEditorInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Role.OWNER);
        shareFolder(internalSharer, sid, internalUser1, Role.VIEWER);

        try {
            updateACL(externalUser1, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }

        try {
            updateACL(externalUser2, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }

        try {
            updateACL(internalUser1, Role.EDITOR);
            fail();
        } catch (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldWarnWhenAddingExternalOwnerToInternalFolder()
            throws Exception
    {
        try {
            shareFolder(internalSharer, sid, externalUser1, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningAddExternalUser e) {}
    }


    @Test
    public void shouldWarnWhenAddingOwnerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        try {
            shareFolder(internalSharer, sid, internalUser1, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers e) {
            sqlTrans.rollback();
        }

        try {
            shareFolder(internalSharer, sid, internalUser2, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldWarnWhenSettingOwnerInExternalFolder()
            throws Exception
    {
        sqlTrans.begin();
        saveUser(externalUser1);
        saveUser(externalUser2);
        saveUser(internalUser1);
        sqlTrans.commit();

        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Role.VIEWER);
        joinSharedFolder(externalUser2, sid);
        shareFolder(internalSharer, sid, internalUser1, Role.VIEWER);
        joinSharedFolder(internalUser1, sid);

        try {
            updateACL(externalUser1, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers e) {
            sqlTrans.rollback();
        }

        try {
            updateACL(externalUser2, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers e) {
            sqlTrans.rollback();
        }

        try {
            updateACL(internalUser1, Role.OWNER);
            fail();
        } catch (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers e) {
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldAllowAddingOwnerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        shareFolderSuppressWarnings(internalSharer, sid, internalUser1, Role.OWNER);
        shareFolderSuppressWarnings(internalSharer, sid, internalUser2, Role.OWNER);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Role.OWNER);
    }

    @Test
    public void shouldAllowSettingOwnerInExternalFolder()
            throws Exception
    {
        sqlTrans.begin();
        saveUser(externalUser1);
        saveUser(externalUser2);
        saveUser(internalUser1);
        sqlTrans.commit();

        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Role.VIEWER);
        joinSharedFolder(externalUser2, sid);
        shareFolder(internalSharer, sid, internalUser1, Role.VIEWER);
        joinSharedFolder(internalUser1, sid);

        updateACLSuppressWarnings(externalUser1, Role.OWNER);
        updateACLSuppressWarnings(externalUser2, Role.OWNER);
        updateACLSuppressWarnings(internalUser1, Role.OWNER);
    }

    @Test
    public void shouldAllowAddingViewerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.VIEWER);

        shareFolder(internalSharer, sid, internalUser1, Role.VIEWER);
        shareFolder(internalSharer, sid, internalUser2, Role.VIEWER);
    }

    @Test
    public void shouldAllowSettingViewerInExternalFolder()
            throws Exception
    {
        sqlTrans.begin();
        saveUser(externalUser1);
        saveUser(externalUser2);
        saveUser(internalUser1);
        sqlTrans.commit();

        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Role.OWNER);
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Role.OWNER);
        joinSharedFolder(externalUser2, sid);
        shareFolderSuppressWarnings(internalSharer, sid, internalUser1, Role.OWNER);
        joinSharedFolder(internalUser1, sid);

        updateACL(externalUser1, Role.VIEWER);
        updateACL(externalUser2, Role.VIEWER);
        updateACL(internalUser1, Role.VIEWER);
    }

    private void updateACL(User user, Role role)
            throws Exception
    {
        service.updateACL(sid.toPB(), user.id().getString(), role.toPB(), false);
    }

    private void updateACLSuppressWarnings(User user, Role role)
            throws Exception
    {
        service.updateACL(sid.toPB(), user.id().getString(), role.toPB(), true);
    }
}
