/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.shared_folder_rules;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Pattern;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

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
        public void onUpdatingACL(SharedFolder sf, User user, Role role, boolean suppressAllWarnings)
        {
        }
    }

    public static ISharedFolderRules create(User.Factory factUser)
    {
        boolean readOnlyExternalFolders =
                getBooleanProperty("shared_folder_rules.readonly_external_folders", false);
        String internalAddresses = getStringProperty("internal_email_pattern", "");

        return readOnlyExternalFolders && !internalAddresses.isEmpty() ?
                new ReadOnlyExternalFolderRules(Pattern.compile(internalAddresses), factUser) :
                new NullSharedFolderRules();
    }
}
