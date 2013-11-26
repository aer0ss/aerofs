/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription.Type;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestSP_RestrictedExternalSharing extends AbstractSPFolderTest
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

        // Authentiator factory reads and caches property values so we have to construct a new one
        SharingRulesFactory sharedFolderRules = new SharingRulesFactory(AuthenticatorFactory.create(),
                factUser, sharedFolderNotificationEmailer);

        // reconstruct SP using the new shared folder rules
        service = new SPService(db, sqlTrans, jedisTrans, sessionUser, passwordManagement,
                certificateAuthenticator, factUser, factOrg, factOrgInvite, factDevice, certdb,
                esdb, factSharedFolder, factEmailer, _deviceRegistrationEmailer,
                requestToSignUpEmailer, commandQueue, analytics, identitySessionManager,
                authenticator, sharedFolderRules, sharedFolderNotificationEmailer);
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

    private void setProperties(boolean enableRestrictedSharing, String internalAddresses)
    {
        Properties props = new Properties();
        props.put(Permissions.RESTRICTED_EXTERNAL_SHARING,
                enableRestrictedSharing ? "true" : "false");
        props.put("internal_email_pattern", internalAddresses);
        ConfigurationProperties.setProperties(props);
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = enableRestrictedSharing;
    }

    @Test
    public void shouldAllowFolderCreatorAsInternalUserToAddInternalUsers()
            throws Exception
    {
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE));
        shareFolder(internalSharer, sid, internalUser2, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
    }

    @Test
    public void shouldAllowSharerAsInternalUserToAddInternalUsers()
            throws Exception
    {
        // create the internal user
        sqlTrans.begin();
        saveUser(internalUser1);
        sqlTrans.commit();

        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        joinSharedFolder(internalUser1, sid);

        shareFolder(internalUser1, sid, internalUser2, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        shareFolder(internalUser1, sid, internalUser3, Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void shouldIgnoreTeamServerUsers()
        throws Exception
    {
        sqlTrans.begin();
        Organization org = factOrg.save();
        sqlTrans.commit();

        shareFolder(internalSharer, sid, org.getTeamServerUser(), Permissions.allOf());
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

        shareFolder(externalUser1, sid, internalUser2, Permissions.allOf());
        shareFolder(externalUser1, sid, externalUser3, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
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

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(externalUser1, sid);

        shareFolder(externalUser1, sid, externalUser2, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        shareFolder(externalUser1, sid, externalUser3, Permissions.allOf());
        try {
            shareFolder(externalUser1, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                    Permission.MANAGE));
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }
        shareFolder(externalUser1, sid, internalUser2, Permissions.allOf());
    }

    @Test
    public void shouldDisallowFolderCreaterAsExternalUserToAddEditors()
            throws Exception
    {
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        try {
            shareFolder(externalUser1, sid, internalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
        }
    }

    @Test
    public void shouldAllowSharerAsExternalUserToAddExternalEditor()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(externalUser1, sid);

        shareFolder(externalUser1, sid, externalUser2, Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void shouldDisallowSharerAsExternalUserToAddInternalEditor() throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(externalUser1);
        sqlTrans.commit();


        try {
            shareFolder(externalUser1, sid, internalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldAllowFolderCreatorAsInternalUserToAddExternalEditors()
            throws Exception
    {
        try {
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf(Permission.WRITE));
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE));
    }

    @Test
    public void shouldAllowSharerAsInternalUserToAddExternalEditors()
            throws Exception
    {
        // create the external user
        sqlTrans.begin();
        saveUser(internalUser1);
        sqlTrans.commit();

        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        joinSharedFolder(internalUser1, sid);

        try {
            shareFolder(internalUser1, sid, externalUser2, Permissions.allOf(Permission.WRITE));
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalUser1, sid, externalUser2, Permissions.allOf(
                Permission.WRITE));
    }

    @Test
    public void shouldWarnWhenAddingExternalUsers()
            throws Exception
    {
        // create an internal folder
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE));

        // attempt to add the first external user
        try {
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf());
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        // add the first external user
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        // attempt to add the second external user
        try {
            shareFolder(internalSharer, sid, externalUser2, Permissions.allOf());
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldConvertEditorsToViewersWhenConvertingInternalFolderToExternal()
            throws Exception
    {
        // create an internal folder
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE));
        shareFolder(internalSharer, sid, internalUser2, Permissions.allOf(Permission.WRITE));
        SharedFolder sf = factSharedFolder.create(sid);

        // make sure both users are an editor
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(internalUser1), Permissions.allOf(Permission.WRITE));
        assertEquals(sf.getPermissionsNullable(internalUser2), Permissions.allOf(Permission.WRITE));
        sqlTrans.commit();

        // convert the folder to an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        // make sure both users are converted to viewers
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(internalUser1), Permissions.allOf());
        assertEquals(sf.getPermissionsNullable(internalUser2), Permissions.allOf());
        sqlTrans.commit();

        // make sure role change notification emails are sent
        verify(sharedFolderNotificationEmailer).sendRoleChangedNotificationEmail(sf, internalSharer,
                internalSharer, Permissions.OWNER, Permissions.allOf(Permission.MANAGE));
        verify(sharedFolderNotificationEmailer).sendRoleChangedNotificationEmail(sf, internalSharer,
                internalUser1, Permissions.EDITOR, Permissions.VIEWER);
        verify(sharedFolderNotificationEmailer).sendRoleChangedNotificationEmail(sf, internalSharer,
                internalUser2, Permissions.EDITOR, Permissions.VIEWER);
        verifyNoMoreInteractions(sharedFolderNotificationEmailer);
    }

    @Test
    public void shouldWarnWhenAddingExternalEditorInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        try {
            shareFolder(internalSharer, sid, internalUser3, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            shareFolder(internalSharer, sid, externalUser2, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
        }
    }

    @Test
    public void shouldDisallowSettingEditorInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf());

        try {
            updateACL(externalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            updateACL(externalUser2, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            updateACL(internalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldWarnWhenAddingExternalOwnerToInternalFolder()
            throws Exception
    {
        try {
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf(Permission.WRITE,
                    Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {}
    }


    @Test
    public void shouldWarnWhenAddingOwnerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        try {
            shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                    Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            shareFolder(internalSharer, sid, internalUser2, Permissions.allOf(Permission.WRITE,
                    Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            shareFolder(internalSharer, sid, externalUser2, Permissions.allOf(Permission.WRITE,
                    Permission.MANAGE));
            fail();
            // When adding an external user as an owner, the system should throw the "adding
            // external user" warning rather than the "owner can share externally" warning
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
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
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Permissions.allOf());
        joinSharedFolder(externalUser2, sid);
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf());
        joinSharedFolder(internalUser1, sid);

        try {
            updateACL(externalUser1, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            updateACL(externalUser2, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            updateACL(internalUser1, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            assertEquals(2, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }
    }

    @Test
    public void shouldAllowAddingOwnerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        shareFolderSuppressWarnings(internalSharer, sid, internalUser1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        shareFolderSuppressWarnings(internalSharer, sid, internalUser2, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
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
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Permissions.allOf());
        joinSharedFolder(externalUser2, sid);
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf());
        joinSharedFolder(internalUser1, sid);

        updateACLSuppressWarnings(externalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        updateACLSuppressWarnings(externalUser2, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        updateACLSuppressWarnings(internalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
    }

    @Test
    public void shouldAllowAddingViewerInExternalFolder()
            throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf());
        shareFolder(internalSharer, sid, internalUser2, Permissions.allOf());
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
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(externalUser1, sid);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser2, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(externalUser2, sid);
        shareFolderSuppressWarnings(internalSharer, sid, internalUser1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(internalUser1, sid);

        updateACL(externalUser1, Permissions.allOf());
        updateACL(externalUser2, Permissions.allOf());
        updateACL(internalUser1, Permissions.allOf());
    }

    private void updateACL(User user, Permissions permissions)
            throws Exception
    {
        service.updateACL(sid.toPB(), user.id().getString(), permissions.toPB(), false);
    }

    private void updateACLSuppressWarnings(User user, Permissions permissions)
            throws Exception
    {
        service.updateACL(sid.toPB(), user.id().getString(), permissions.toPB(), true);
    }
}
