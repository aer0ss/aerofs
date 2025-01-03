/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.sharing_rules;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;

import javax.inject.Inject;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;

public class SharingRulesFactory
{
    final Authenticator _authenticator;
    final Factory _factUser;
    final SharedFolderNotificationEmailer _sfnEmailer;

    @Inject
    public SharingRulesFactory(Authenticator authenticator, Factory factUser,
            SharedFolderNotificationEmailer sfnEmailer)
    {
        _authenticator = authenticator;
        _factUser = factUser;
        _sfnEmailer = sfnEmailer;
    }

    private static class DefaultSharingRules implements ISharingRules
    {
        @Override
        public Permissions onUpdatingACL(SharedFolder sf, User sharee, Permissions newPermissions)
        {
            return newPermissions;
        }

        @Override
        public Permissions onUpdatingACL(SharedFolder sf, Group sharee, Permissions newPermissions)
                throws Exception
        {
            return newPermissions;
        }

        @Override
        public void throwIfAnyWarningTriggered()
        {}

        @Override
        public boolean shouldBumpEpoch()
        {
            return false;
        }
    }

    public ISharingRules create(User sharer)
            throws Exception
    {
        boolean readOnlyExternalFolders =
                getBooleanProperty(Permissions.RESTRICTED_EXTERNAL_SHARING, false);

        //If the user is an external user, they will have no permission to share
        if (readOnlyExternalFolders && !_authenticator.isInternalUser(sharer.id())) {
            throw new ExNoPerm("External members are not allowed to share or manage folder");
        }

        return readOnlyExternalFolders ?
                new RestrictedExternalSharing(this, sharer) :
                new DefaultSharingRules();
    }
}
