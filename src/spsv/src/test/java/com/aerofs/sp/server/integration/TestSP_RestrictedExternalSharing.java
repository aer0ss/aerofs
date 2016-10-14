/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExWrongOrganization;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription.Type;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.google.common.collect.Lists;
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

    // the user ids that match the internal email pattern
    User internalUser1 = factUser.create(UserID.fromInternal("abc@adf"));
    User internalUser2 = factUser.create(UserID.fromInternal("xocjaxyz"));
    User internalUser3 = factUser.create(UserID.fromInternal("xyz"));

    // the user ids that don't match the internal email pattern
    User externalUser1 = factUser.create(UserID.fromInternal("haabc@adf"));
    User externalUser2 = factUser.create(UserID.fromInternal("xyza"));
    User externalUser3 = factUser.create(UserID.fromInternal("xyzabc"));

    SID sid = SID.generate();
    // This user is an internal user
    User internalSharer = factUser.create(UserID.fromInternal("abc"));

    // This user is internal but has been whitelisted by an admin
    User whitelistedUser = factUser.create(UserID.fromInternal("abcdef@g"));

    Organization org;
    Group group;

    @Before
    public void setup()
            throws Exception
    {
        // Ask SharedFolderRulesFactory to create ReadOnlyExternalFolderRules
        setProperties(true, INTERNAL_ADDRESSES);

        // Authenticator factory reads and caches property values so we have to construct a new one
        AuthenticatorFactory authFactory = new AuthenticatorFactory(aclNotificationPublisher, auditClient, analyticsClient);
        authenticator = authFactory.create();
        sharingRules = new SharingRulesFactory(authenticator, factUser, sharedFolderNotificationEmailer);

        // reconstruct SP using the new shared folder rules
        rebuildSPService();

        sqlTrans.begin();
        saveUser(internalSharer);
        internalSharer.setLevel(AuthorizationLevel.ADMIN);
        internalSharer.setWhitelisted(true);
        saveUser(internalUser1);
        saveUser(internalUser2);
        saveUser(internalUser3);
        saveUser(externalUser1);
        saveUser(externalUser2);
        saveUser(externalUser3);
        saveUser(whitelistedUser);
        whitelistedUser.setWhitelisted(true);
        org = internalSharer.getOrganization();
        group = factGroup.save("Common Name", org.id(), null);
        makeUsersMembersOfOrganization(org, internalUser1, internalUser2, internalUser3,
                externalUser1, externalUser2, externalUser3, whitelistedUser);
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
    }

    @Test
    public void shouldAllowFolderCreatorAsInternalUserToAddInternalUsers()
            throws Exception
    {
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE));
        shareFolder(internalSharer, sid, internalUser2,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE));
    }

    @Test
    public void shouldAllowSharerAsInternalUserToAddInternalUsers()
            throws Exception
    {
        // create the internal user
        sqlTrans.begin();
        sqlTrans.commit();

        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        joinSharedFolder(internalUser1, sid);

        shareFolder(internalUser1, sid, internalUser2,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE));
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

    @Test
    public void shouldNotAllowExternalUserAsOwner()
            throws Exception
    {
        try {
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        joinSharedFolder(externalUser1, sid);
        try {
            shareFolder(externalUser1, sid, externalUser2,
                    Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldDisallowExternalUserToShareFolder()
            throws Exception
    {
        try {
            shareFolder(externalUser1, sid, internalUser1, Permissions.allOf());
            fail();
        }catch (ExNoPerm e){}

    }

    @Test
    public void shouldDisallowSharerAsExternalUserToAddInternalEditor() throws Exception
    {
        try {
            shareFolder(externalUser1, sid, internalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExNoPerm e) {}
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
    public void shouldNotAllowSharerAsInternalUserWithoutPublisherToAddExternalEditors()
            throws Exception
    {
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        joinSharedFolder(internalUser1, sid);

        try {
            shareFolder(internalUser1, sid, externalUser2, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExNoPerm e) {}

    }

    @Test
    public void shouldAllowInternalUserToInviteOtherInternalUserWithReadOnlyInExternalFolder()
        throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);

        try {
            //internalUser1 with Manage permission
            shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.MANAGE));
            joinSharedFolder(internalUser1, sid);

            //Switch to externally shared folder
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf());
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE));

        joinSharedFolder(externalUser1, sid);

        try {
            shareFolder(internalUser1, sid, internalUser2, Permissions.allOf());
        } catch (ExSharingRulesWarning e){
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalUser1, sid, internalUser2, Permissions.allOf());
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(internalUser2), Permissions.allOf());
        sqlTrans.commit();
    }

    @Test
    public void shouldWarnDowngradeWhenInternalUserInviteOtherInternalUserWithWritePermissionInExternalFolder()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);

        try {
            shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.MANAGE));
            joinSharedFolder(internalUser1, sid);

            //Switch to externally shared folder
            shareFolder(internalSharer, sid, externalUser1, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_EXTERNAL_SHARING, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf(
                Permission.WRITE));

        joinSharedFolder(externalUser1, sid);

        try {
            shareFolder(internalUser1, sid, internalUser2, Permissions.allOf(Permission.WRITE));
        } catch (ExSharingRulesWarning e){
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_DOWNGRADE, e.descriptions().get(0).type);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalUser1, sid, internalUser2, Permissions.allOf(Permission.WRITE));

        //internalUser2 should only have read-only since it is not a publisher
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(internalUser2), Permissions.allOf());
        sqlTrans.commit();

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
                internalUser1, Permissions.EDITOR, Permissions.VIEWER);
        verify(sharedFolderNotificationEmailer).sendRoleChangedNotificationEmail(sf, internalSharer,
                internalUser2, Permissions.EDITOR, Permissions.VIEWER);
        verifyNoMoreInteractions(sharedFolderNotificationEmailer);
    }

    @Test
    public void shouldNotConvertWhitelistedEditorToViewerWhenConvertingInternalFolderToExternal()
            throws Exception
    {
        // create an internal folder
        shareFolder(internalSharer, sid, whitelistedUser, Permissions.allOf(Permission.WRITE));
        SharedFolder sf = factSharedFolder.create(sid);

        // make sure user is an editor
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(whitelistedUser), Permissions.allOf(Permission.WRITE));
        sqlTrans.commit();

        // convert the folder to an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());

        // make sure user isn't converted to viewer
        sqlTrans.begin();
        assertEquals(sf.getPermissionsNullable(whitelistedUser),
                Permissions.allOf(Permission.WRITE));
        sqlTrans.commit();
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

        updateACL(externalUser1, Permissions.allOf(Permission.WRITE));
        updateACL(externalUser2, Permissions.allOf(Permission.WRITE));

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
    public void shouldAllowSettingWhitelistedUserAsEditorInExternalFolder()
        throws Exception
    {
        // make an external folder
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        shareFolderSuppressWarnings(internalSharer, sid, whitelistedUser, Permissions.allOf(Permission.WRITE));
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
            shareFolder(internalSharer, sid, externalUser2, Permissions.allOf(Permission.WRITE));
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
            assertEquals(Type.WARNING_NO_EXTERNAL_OWNERS, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
            sqlTrans.rollback();
        }

        try {
            updateACL(externalUser2, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(1, e.descriptions().size());
            assertEquals(Type.WARNING_NO_EXTERNAL_OWNERS, e.descriptions().get(0).type);
            assertEquals(1, e.descriptions().get(0).users.size());
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

        try {
            shareFolderSuppressWarnings(externalUser1, sid, internalUser2, Permissions.allOf());
            fail();
        } catch (ExNoPerm e) {
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

    @Test
    public void shouldBehaveNormallyWithInternalGroups()
            throws Exception
    {
        setSession(internalSharer);
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(internalUser1.id().getString(), internalUser2.id().getString()));
        shareFolder(internalSharer, sid, group, Permissions.OWNER);
    }

    @Test(expected=ExWrongOrganization.class)
    public void shouldDenyAddingExternalMemberToGroup()
            throws Exception
    {
        setSession(internalSharer);
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(internalUser1.id().getString(), internalUser2.id().getString()));
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(externalUser1.id().getString()));
    }

    @Test
    public void shouldRemoveWriteUponAddingExternalGroup()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);
        setSession(internalSharer);
        addExternalMemberToGroup(externalUser1, group);
        shareFolder(internalSharer, sid, internalUser1, Permissions.allOf(Permission.WRITE));
        try {
            shareFolder(internalSharer, sid, group, Permissions.allOf());
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(e.descriptions().size(), 1);
            assertEquals(e.descriptions().get(0).type, Type.WARNING_EXTERNAL_SHARING);
            sqlTrans.rollback();
        }
        shareFolderSuppressWarnings(internalSharer, sid, group, Permissions.allOf());
        sqlTrans.begin();
        assertEquals(sf.getPermissions(internalUser1), Permissions.allOf());
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveManageFromExternalGroup()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);
        setSession(internalSharer);
        addExternalMemberToGroup(externalUser1, group);
        addExternalMemberToGroup(externalUser2, group);
        try {
            shareFolder(internalSharer, sid, group, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
            fail();
        } catch (ExSharingRulesWarning e) {
            assertEquals(e.descriptions().size(), 2);
            assertEquals(e.descriptions().get(0).type, Type.WARNING_EXTERNAL_SHARING);
            assertEquals(e.descriptions().get(1).type, Type.WARNING_NO_EXTERNAL_OWNERS);
            sqlTrans.rollback();
        }

        shareFolderSuppressWarnings(internalSharer, sid, group, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        sqlTrans.begin();
        assertEquals(sf.getPermissions(externalUser1), Permissions.allOf(Permission.WRITE));
        assertEquals(sf.getPermissions(externalUser2), Permissions.allOf(Permission.WRITE));
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveWriteFromGroupInExternalFolder()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);
        setSession(internalSharer);
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(internalUser1.id().getString(), internalUser2.id().getString()));
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        try {
            shareFolder(internalSharer, sid, group, Permissions.allOf(Permission.WRITE));
        } catch (ExSharingRulesWarning e) {
            assertEquals(e.descriptions().size(), 1);
            assertEquals(e.descriptions().get(0).type, Type.WARNING_DOWNGRADE);
            sqlTrans.rollback();
        }
        shareFolderSuppressWarnings(internalSharer, sid, group, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        sqlTrans.begin();
        assertEquals(sf.getPermissions(internalUser1), Permissions.allOf(Permission.MANAGE));
        assertEquals(sf.getPermissions(internalUser2), Permissions.allOf(Permission.MANAGE));
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveWriteFromGroupAndUsers()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);
        setSession(internalSharer);
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(internalUser1.id().getString(), internalUser2.id().getString()));
        shareFolder(internalSharer, sid, internalUser1, Permissions.OWNER);
        shareFolderSuppressWarnings(internalSharer, sid, externalUser1, Permissions.allOf());
        shareFolderSuppressWarnings(internalSharer, sid, group, Permissions.allOf(Permission.WRITE));
        sqlTrans.begin();
        assertEquals(sf.getPermissions(internalUser1), Permissions.allOf(Permission.MANAGE));
        assertEquals(sf.getPermissions(internalUser2), Permissions.allOf());
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveManageAndWrite()
            throws Exception
    {
        SharedFolder sf = factSharedFolder.create(sid);
        setSession(internalSharer);
        service.addGroupMembers(group.id().getInt(),
                Lists.newArrayList(internalUser1.id().getString()));
        addExternalMemberToGroup(externalUser1, group);
        shareFolderSuppressWarnings(internalSharer, sid, group,
                Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        sqlTrans.begin();
        // group contains internal and external users, should strip both write and manage permissions
        assertEquals(sf.getPermissions(internalUser1), Permissions.allOf());
        assertEquals(sf.getPermissions(externalUser1), Permissions.allOf());
        sqlTrans.commit();
    }

    private void updateACL(User user, Permissions permissions)
            throws Exception
    {
        service.updateACL(BaseUtil.toPB(sid), user.id().getString(), permissions.toPB(), false);
    }

    private void updateACLSuppressWarnings(User user, Permissions permissions)
            throws Exception
    {
        service.updateACL(BaseUtil.toPB(sid), user.id().getString(), permissions.toPB(), true);
    }

    private void addExternalMemberToGroup(User external, Group group)
            throws Exception
    {
        if (sqlTrans.isInTransaction()) {
            group.addMember(external);
        } else {
            sqlTrans.begin();
            try {
                group.addMember(external);
                sqlTrans.commit();
            } catch (Throwable t) {
                sqlTrans.rollback();
                throw t;
            } finally {
                sqlTrans.cleanUp();
            }
        }
    }

    private void makeUsersMembersOfOrganization(Organization org, User... users)
            throws Exception
    {
        for (User u : users) {
            u.setOrganization(org, AuthorizationLevel.USER);
        }
    }
}
