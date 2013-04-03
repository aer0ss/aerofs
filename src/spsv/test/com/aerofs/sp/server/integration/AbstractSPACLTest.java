package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.lib.Param;
import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
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
    private long getInitialServerACL()
    {
        //noinspection PointlessArithmeticExpression
        return Param.INITIAL_ACL_EPOCH + 1;
    }

    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();

        // remove all root stores to simplify test verifications.
        sfdb.delete(SID.rootSID(USER_1.id()));
        sfdb.delete(SID.rootSID(USER_2.id()));
        sfdb.delete(SID.rootSID(USER_3.id()));

        sqlTrans.commit();
    }

    protected static class UserAndRole {
        User u;
        Role r;

        @Override
        public String toString()
        {
            return u + ": " + r;
        }

        UserAndRole(User u, Role r)
        {
            this.u = u;
            this.r = r;
        }
    }

    protected void assertGetACLReplyIncrementsEpochBy(GetACLReply reply, int delta)
    {
        assertEquals(getInitialServerACL() + delta, reply.getEpoch());
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

    protected List<PBSubjectRolePair> getSingleACL(SID sid, GetACLReply getACLReply)
    {
        assertEquals(1, getACLReply.getStoreAclCount());
        assertEquals(sid.toPB(), getACLReply.getStoreAcl(0).getStoreId());
        return getACLReply.getStoreAcl(0).getSubjectRoleList();
    }

    protected void assertACLOnlyContains(List<PBSubjectRolePair> pairs, User user, Role role)
            throws Exception
    {
        assertACLOnlyContains(pairs, new UserAndRole(user, role));
    }

    protected void assertACLOnlyContains(List<PBSubjectRolePair> pairs, UserAndRole ... urs)
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
            assertACLContains(pairs, tsUser, Role.EDITOR);
        }

        if (pairs.size() != urs.length + tsUsers.size()) {
            StringBuilder sb = new StringBuilder("[");
            for (PBSubjectRolePair pair : pairs) {
                sb.append(pair.getSubject())
                        .append(": ")
                        .append(Role.fromPB(pair.getRole()))
                        .append(", ");
            }
            sb.append("]");

            fail("getACL() returns mismatch expected: " + Arrays.toString(urs) + " + team servers," +
                    " actual: " + sb.toString());
        }
    }

    // FIXME: [sigh] think up a more efficient way
    private void assertACLContains(List<PBSubjectRolePair> pairs, User subject, Role role)
            throws ExEmptyEmailAddress
    {
        boolean found = false;

        for (PBSubjectRolePair pair : pairs) {
            try {
                UserID currentSubject = UserID.fromExternal(pair.getSubject());
                Role actualRole = Role.fromPB(pair.getRole());

                if (currentSubject.equals(subject.id()) && actualRole.equals(role)) {
                    found = true;
                } else if (currentSubject.equals(subject.id())) {
                    fail(currentSubject + " expect role: " + role + " actual: " + actualRole);
                }
            } catch (ExBadArgs exBadArgs) {
                fail("no role for " + pair.getRole().name());
            }
        }

        assertTrue("no entry for " + subject + ": " + role, found);
    }
}
