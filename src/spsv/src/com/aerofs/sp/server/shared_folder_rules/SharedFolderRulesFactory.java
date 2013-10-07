/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.shared_folder_rules;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.UserFilter;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.util.List;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;

public class SharedFolderRulesFactory
{
    private static class NullSharedFolderRules implements ISharedFolderRules
    {
        @Override
        public @Nonnull ImmutableCollection<UserID> onInvitingUsers(User sharer, SharedFolder sf,
                List<SubjectRolePair> srps, boolean suppressAllWarnings)
        {
            return ImmutableSet.of();
        }

        @Override
        public void onUpdatingACL(SharedFolder sf, Role role, boolean suppressAllWarnings)
        {
        }
    }

    public static ISharedFolderRules create(User.Factory factUser,
            SharedFolderNotificationEmailer sfnEmailer)
    {
        boolean readOnlyExternalFolders =
                getBooleanProperty("shared_folder_rules.readonly_external_folders", false);

        return readOnlyExternalFolders ?
                new ReadOnlyExternalFolderRules(new UserFilter(), factUser, sfnEmailer) :
                new NullSharedFolderRules();
    }
}
