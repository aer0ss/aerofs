package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Sets;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractSPACLTest extends AbstractSPFolderTest
{
    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();

        // remove all root stores to simplify test verifications.
        sfdb.destroy(SID.rootSID(USER_1.id()));
        sfdb.destroy(SID.rootSID(USER_2.id()));
        sfdb.destroy(SID.rootSID(USER_3.id()));

        sqlTrans.commit();
    }

    protected static class UserAndRole {
        User u;
        Permissions r;

        @Override
        public String toString()
        {
            return u + ": " + r;
        }

        UserAndRole(User u, Permissions r)
        {
            this.u = u;
            this.r = r;
        }
    }

    protected void assertGetACLReplyIncrementsEpochBy(GetACLReply reply, int delta)
    {
        //noinspection PointlessArithmeticExpression
        assertEquals((long) 1 + delta, reply.getEpoch());
    }

    protected User addAdmin(User user)
            throws Exception
    {
        sqlTrans.begin();
        User admin = saveUser();
        admin.setOrganization(user.getOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();
        return admin;
    }

    protected List<PBSubjectPermissions> getSingleACL(SID sid, GetACLReply getACLReply)
    {
        assertEquals(1, getACLReply.getStoreAclCount());
        assertEquals(BaseUtil.toPB(sid), getACLReply.getStoreAcl(0).getStoreId());
        return getACLReply.getStoreAcl(0).getSubjectPermissionsList();
    }

    protected void assertACLOnlyContains(List<PBSubjectPermissions> pairs, User user, Permissions permissions)
            throws Exception
    {
        assertACLOnlyContains(pairs, new UserAndRole(user, permissions));
    }

    protected void assertACLOnlyContains(List<PBSubjectPermissions> pairs, UserAndRole ... urs)
            throws Exception
    {
        Set<User> tsUsers = Sets.newHashSet();

        sqlTrans.begin();
        for (UserAndRole ur : urs) {
            assertACLContains(pairs, ur.u, ur.r);
            tsUsers.add(ur.u.getOrganization().getTeamServerUser());
        }
        sqlTrans.commit();

        // verify the team server of all the users exist in the ACL
        for (User tsUser : tsUsers) {
            assertACLContains(pairs, tsUser, Permissions.allOf(Permission.WRITE));
        }

        if (pairs.size() != urs.length + tsUsers.size()) {
            StringBuilder sb = new StringBuilder("[");
            for (PBSubjectPermissions pair : pairs) {
                sb.append(pair.getSubject())
                        .append(": ")
                        .append(Permissions.fromPB(pair.getPermissions()))
                        .append(", ");
            }
            sb.append("]");

            fail("getACL() returns mismatch expected: " + Arrays.toString(urs) + " + Team Servers," +
                    " actual: " + sb.toString());
        }
    }

    // FIXME: [sigh] think up a more efficient way
    private void assertACLContains(List<PBSubjectPermissions> pairs, User subject, Permissions permissions)
            throws ExInvalidID
    {
        boolean found = false;

        for (PBSubjectPermissions pair : pairs) {
            UserID currentSubject = UserID.fromExternal(pair.getSubject());
            Permissions actualPermissions = Permissions.fromPB(pair.getPermissions());

            if (currentSubject.equals(subject.id()) && actualPermissions.equals(permissions)) {
                found = true;
            } else if (currentSubject.equals(subject.id())) {
                fail(currentSubject + " expect role: " + permissions + " actual: " +
                        actualPermissions);
            }
        }

        assertTrue("no entry for " + subject + ": " + permissions, found);
    }
}
