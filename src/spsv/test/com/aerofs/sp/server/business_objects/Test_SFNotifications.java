package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static com.aerofs.servlets.lib.ThreadLocalSFNotifications.SFNotificationMessage.*;
import static org.mockito.Mockito.*;


public class Test_SFNotifications extends AbstractBusinessObjectTest {
    protected User owner;
    protected SharedFolder sf;
    protected Organization org;

    @Before
    public void setup() throws Exception
    {
        owner = saveUser();
        SID sid = SID.generate();
        org = factOrg.save(new OrganizationID(1));
        sf = factSharedFolder.create(sid);
        sf.save("shared folder", owner);
    }

    @Test
    public void shouldAddNotifOnAddingAMember()
            throws Exception
    {
        User member = saveUser();
        sf.addJoinedUser(member, Permissions.EDITOR);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifOnRemovingMember()
            throws Exception
    {
        User member = saveUser();
        sf.addJoinedUser(member, Permissions.EDITOR);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        sf.removeIndividualUser(member);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), LEAVE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifOnMemberLeaving()
            throws Exception
    {
        User member = saveUser();
        sf.addJoinedUser(member, Permissions.EDITOR);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        sf.setState(member, SharedFolderState.LEFT);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), LEAVE);
        sf.setState(member, SharedFolderState.JOINED);
        verify(sfNotif, times(2)).addNotif(member.id(), sf.id(), JOIN);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifWhenUserAcceptsInvitation()
            throws Exception
    {
        User member = saveUser();
        sf.addPendingUser(member, Permissions.EDITOR, owner);
        verifyZeroInteractions(sfNotif);
        sf.setState(member, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifOnMemberChangingPerms()
            throws Exception
    {
        User member = saveUser();
        sf.addJoinedUser(member, Permissions.EDITOR);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        sf.setPermissions(member, Permissions.OWNER);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), CHANGE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifFromGroupJoins()
            throws Exception
    {
        User member = saveUser();
        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        // user is added as pending, so no notif
        verifyZeroInteractions(sfNotif);

        sf.setState(member, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        User member2 = saveUser();
        g.addMember(member2);
        sf.setState(member2, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void groupJoinShouldAddCorrectNotifIfAlreadyMember()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser(), member3 = saveUser();
        sf.addJoinedUser(member, Permissions.EDITOR);
        sf.addJoinedUser(member2, Permissions.VIEWER);
        sf.addJoinedUser(member3, Permissions.OWNER);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member3.id(), sf.id(), JOIN);

        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.addMember(member2);
        g.addMember(member3);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        // no change for member1 and member3, and no notif
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), CHANGE);

        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifFromGroupLeaving()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser();
        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.addMember(member2);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        sf.setState(member, SharedFolderState.JOINED);
        sf.setState(member2, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);

        // use the same method that sp would for a direct invite
        sf.addUserWithGroup(member2, null, Permissions.EDITOR, owner);

        g.deleteSharedFolder(sf);
        // member2 is a direct member of the shared folder, and so should not leave it
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), LEAVE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifFromGroupChangingPerms()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser();
        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.addMember(member2);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        sf.setState(member, SharedFolderState.JOINED);
        sf.setState(member2, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);

        // use the same method that sp would for a direct invite
        sf.addUserWithGroup(member2, null, Permissions.EDITOR, owner);

        g.changeRoleInSharedFolder(sf, Permissions.VIEWER);
        // member2's permissions did not change
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), CHANGE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldAddNotifWhenGroupPermsAlreadyExist()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser();
        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.addMember(member2);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        sf.setState(member, SharedFolderState.JOINED);
        sf.setState(member2, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);

        // use the same method that sp would for a direct invite
        sf.addUserWithGroup(member, null, Permissions.EDITOR, owner);
        sf.addUserWithGroup(member2, null, Permissions.OWNER, owner);

        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), CHANGE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldSendNotifWhenMemberAddedToGroup()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser(), member3 = saveUser();
        sf.addJoinedUser(member, Permissions.VIEWER);
        sf.addJoinedUser(member2, Permissions.EDITOR);

        // add member3 as pending
        sf.addUserWithGroup(member3, null, Permissions.VIEWER, owner);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);

        Group g = factGroup.save("Common Name", org.id(), null);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        g.addMember(member);
        g.addMember(member2);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), CHANGE);
        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldSendNotifWhenMemberRemovedFromGroup()
            throws Exception
    {
        User member = saveUser(), member2 = saveUser(), member3 = saveUser();
        Group g = factGroup.save("Common Name", org.id(), null);
        g.addMember(member);
        g.addMember(member2);
        g.addMember(member3);
        g.joinSharedFolder(sf, Permissions.EDITOR, owner);
        sf.setState(member, SharedFolderState.JOINED);
        sf.setState(member2, SharedFolderState.JOINED);
        sf.setState(member3, SharedFolderState.JOINED);
        verify(sfNotif, times(1)).addNotif(member.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), JOIN);
        verify(sfNotif, times(1)).addNotif(member3.id(), sf.id(), JOIN);

        // use the same method that sp would for a direct invite
        sf.addUserWithGroup(member, null, Permissions.EDITOR, owner);
        sf.addUserWithGroup(member2, null, Permissions.VIEWER, owner);

        g.removeMember(member, null);
        verifyNoMoreInteractions(sfNotif);
        g.removeMember(member2, null);
        verify(sfNotif, times(1)).addNotif(member2.id(), sf.id(), CHANGE);
        g.removeMember(member3, null);
        verify(sfNotif, times(1)).addNotif(member3.id(), sf.id(), LEAVE);

        verifyNoMoreInteractions(sfNotif);
    }

    @Test
    public void shouldNotSendNotifForRootStoreOrTeamserver()
            throws Exception
    {
        saveUser();
        // user joined root store, but shouldn't have a notif
        // TS also joins the user root store, no notif for that either
        verifyZeroInteractions(sfNotif);
    }
}
