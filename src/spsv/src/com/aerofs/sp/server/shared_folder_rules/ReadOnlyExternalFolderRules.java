/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.shared_folder_rules;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningConvertToExternallySharedFolder;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This rule is to fulfill Bloomberg's requirements which is listed below. See
 * TestSP_ReadOnlyExternalFolderRules for a formal spec.
 *
 * O When adding external users to a shared folder that contains no
 * external users, either through Desktop or Web UI, the system:
 * - show the warning: "1) be careful when sharing files with
 * external parties. 2) ensure that all existing files in the folder have
 * nothing confidential. 3) all existing editors of the folder will be
 * automatically converted to viewers."
 * - convert all editors to viewers.
 *
 * o When adding or setting _editors_ to a shared folder that contains
 * external users, the system rejects the operation with an error "Only
 * owners or viewers are allowed for externally shared folders. This
 * folder is shared with external users foo, bar, baz."
 *
 * o When adding or setting _owners_ to a shared folder that contains
 * external users, the system shows the warning: "Owners of this folder
 * will be able to share files with external users foo, bar, baz."
 */
public class ReadOnlyExternalFolderRules implements ISharedFolderRules
{
    private final Pattern _emailWhitelist;

    public ReadOnlyExternalFolderRules(Pattern emailWhitelist)
    {
        _emailWhitelist = emailWhitelist;
    }

    @Override
    public @Nonnull ImmutableCollection<UserID> onInvitingUsers(User sharer, SharedFolder sf,
            List<SubjectRolePair> srps, boolean suppressAllWarnings)
            throws Exception
    {
        // figure out the situation
        boolean wasExternal = !getExternalUsers(sf).isEmpty();
        boolean convertToExternal;
        if (wasExternal) {
            convertToExternal = false;
        } else {
            convertToExternal = false;
            for (SubjectRolePair srp : srps) {
                if (isExternalUser(srp._subject)) {
                    convertToExternal = true;
                    break;
                }
            }
        }

        // show warning messages only if the sharer is an internal user
        if (!suppressAllWarnings && !isExternalUser(sharer.id())) {
            showWarningsForExternalFolders(wasExternal, convertToExternal, srps);
        }

        // check enforcement _after_ showing warnings to the user (see the above line), so that
        // error messages, if any, comes after the warning message. See also onUpdatingACL()
        if (wasExternal || convertToExternal) throwIfInvitingEditors(srps);

        ImmutableCollection<UserID> users;
        if (convertToExternal) users = convertEditorsToViewers(sf);
        else users = ImmutableList.of();

        return users;
    }

    // convert existing editors to viewers, skip Team Servers
    private ImmutableCollection<UserID> convertEditorsToViewers(SharedFolder sf)
            throws SQLException, ExNoAdminOrOwner, ExNotFound
    {
        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();
        for (User user : sf.getAllUsers()) {
            if (user.id().isTeamServerID()) continue;
            if (sf.getRole(user).equals(Role.EDITOR)) {
                builder.addAll(sf.updateACL(user, Role.VIEWER));
            }
        }
        return builder.build();
    }

    private void showWarningsForExternalFolders(boolean wasExternal, boolean convertToExternal,
            List<SubjectRolePair> srps)
            throws ExSharedFolderRulesWarningConvertToExternallySharedFolder,
            ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers
    {
        if (convertToExternal) {
            // warn that the folder is about to be shared externally
            throw new ExSharedFolderRulesWarningConvertToExternallySharedFolder();
        }

        if (wasExternal) {
            // if there is an owner in the invitation list, warn that the owners will be able to
            // share files with existing external users
            for (SubjectRolePair srp : srps) {
                if (srp._role.equals(Role.OWNER)) {
                    throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers();
                }
            }
        }
    }

    private void throwIfInvitingEditors(List<SubjectRolePair> srps)
            throws ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders
    {
        for (SubjectRolePair srp : srps) {
            if (srp._role.equals(Role.EDITOR)) {
                throw new ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders();
            }
        }
    }

    @Override
    public void onUpdatingACL(SharedFolder sf, User user, Role role, boolean suppressAllWarnings)
            throws Exception
    {
        boolean isExternalFolder = !getExternalUsers(sf).isEmpty();

        if (!suppressAllWarnings && isExternalFolder && role.equals(Role.OWNER)) {
            // warn that the new owner will be able to share files with existing external users
            throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers();
        }

        // Do not allow editors on externally shared folders
        if (isExternalFolder && role.equals(Role.EDITOR)) {
            throw new ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders();
        }
    }

    /**
     * @return the collection of the folder's users whose email addresses don't match the white
     * list. Return an empty collection if the folder is not shared externally.
     */
    private ImmutableCollection<User> getExternalUsers(SharedFolder sf)
            throws SQLException
    {
        ImmutableList.Builder<User> builder = ImmutableList.builder();
        for (User user : sf.getAllUsers()) {
            UserID id = user.id();
            if (!id.isTeamServerID() && isExternalUser(id)) builder.add(user);
        }
        return builder.build();
    }

    private boolean isExternalUser(UserID id)
    {
        return !_emailWhitelist.matcher(id.getString()).matches();
    }
}
