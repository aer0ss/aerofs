package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

public class TestSP_ListGroupStatusInSharedFolder extends AbstractSPFolderTest
{
    @Test
    public void shouldListWhenGroupMemberHasNotSignedUp()
        throws Exception
    {
        sqlTrans.begin();

        User sharer = saveUser();
        Organization org = sharer.getOrganization();
        Group group = factGroup.save("My Group", org.id(), null);
        User nonExistentUser = newUser();
        group.addMember(nonExistentUser);

        sqlTrans.commit();

        shareFolder(sharer, SID_1, group, Permissions.EDITOR);

        service.listGroupStatusInSharedFolder(group.id().getInt(), BaseUtil.toPB(SID_1));
    }
}
