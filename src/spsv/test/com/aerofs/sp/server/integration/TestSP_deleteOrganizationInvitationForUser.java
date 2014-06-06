/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.FullName;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_deleteOrganizationInvitationForUser extends AbstractSPFolderTest
{
    @Test
    public void shouldDeleteAllSignupCodesAndEmailSubscriptionInPrivateDeployment()
            throws Exception
    {
        User user = newUser();

        sqlTrans.begin();
        User admin = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);

        String codes[] = {
                user.addSignUpCode(),
                user.addSignUpCode(),
                user.addSignUpCode(),
        };

        user.save(new byte[0], new FullName("", ""));
        for (String code : codes) {
            assertEquals(db.getSignUpCode(code), user.id());
        }
        sqlTrans.commit();

        setSession(admin);
        service.inviteToOrganization(user.id().getString());

        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        service.deleteOrganizationInvitationForUser(user.id().getString());

        sqlTrans.begin();
        for (String code : codes) {
            try {
                db.getSignUpCode(code);
                fail();
            } catch (ExNotFound e) {}
        }
        sqlTrans.commit();
    }
}
