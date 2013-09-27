/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.shared_folder_rules;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningConvertToExternallySharedFolder;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
    private final Pattern _internalAddresses;
    private final User.Factory _factUser;

    public ReadOnlyExternalFolderRules(Pattern internalAddresses, User.Factory factUser)
    {
        _internalAddresses = internalAddresses;
        _factUser = factUser;
    }

    @Override
    public @Nonnull ImmutableCollection<UserID> onInvitingUsers(User sharer, SharedFolder sf,
            List<SubjectRolePair> srps, boolean suppressAllWarnings)
            throws Exception
    {
        ImmutableCollection<UserID> oldExternal = getExternalUsers(sf);
        ImmutableCollection<UserID> newExternal = getExternalUsers(srps);
        // N.B. don't call getFullNames() now to avoid unnecessary overhead
        ImmutableCollection<UserID> allExternal = ImmutableSet.<UserID>builder()
                .addAll(oldExternal)
                .addAll(newExternal)
                .build();

        // figure out the situation
        boolean wasExternal = !oldExternal.isEmpty();
        boolean convertToExternal = !wasExternal && !newExternal.isEmpty();

        // show warning messages only if the sharer is an internal user
        if (!suppressAllWarnings && !isExternalUser(sharer.id())) {
            showWarningsForExternalFolders(wasExternal, convertToExternal, srps, allExternal);
        }

        // check enforcement _after_ showing warnings to the user (see the above line), so that
        // error messages, if any, comes after the warning message. See also onUpdatingACL()
        if (wasExternal || convertToExternal) throwIfInvitingEditors(srps, allExternal);

        ImmutableCollection<UserID> users;
        if (convertToExternal) users = convertEditorsToViewers(sf);
        else users = ImmutableList.of();

        return users;
    }

    private ImmutableCollection<UserID> getExternalUsers(List<SubjectRolePair> srps)
    {
        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();
        for (SubjectRolePair srp : srps) {
            if (isExternalUser(srp._subject)) builder.add(srp._subject);
        }
        return builder.build();
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
            List<SubjectRolePair> srps, ImmutableCollection<UserID> externalUsers)
            throws Exception
    {
        if (convertToExternal) {
            // warn that the folder is about to be shared externally
            throw new ExSharedFolderRulesWarningConvertToExternallySharedFolder(
                    getFullNames(externalUsers));
        }

        if (wasExternal) {
            // if there is an owner in the invitation list, warn that the owners will be able to
            // share files with existing external users
            for (SubjectRolePair srp : srps) {
                if (srp._role.equals(Role.OWNER)) {
                    throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(
                            getFullNames(externalUsers));
                }
            }
        }
    }

    private ImmutableMap<UserID, FullName> getFullNames(ImmutableCollection<UserID> userIDs)
            throws SQLException, ExNotFound
    {
        ImmutableMap.Builder<UserID, FullName> builder = ImmutableMap.builder();
        for (UserID userID : userIDs) {
            User user = _factUser.create(userID);
            // Use empty names if the user hasn't signed up.
            builder.put(userID, user.exists() ? user.getFullName() : new FullName("", ""));
        }
        return builder.build();
    }

    private void throwIfInvitingEditors(List<SubjectRolePair> srps,
            ImmutableCollection<UserID> externalUsers)
            throws Exception
    {
        for (SubjectRolePair srp : srps) {
            if (srp._role.equals(Role.EDITOR)) {
                throw new ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders(
                        getFullNames(externalUsers));
            }
        }
    }

    @Override
    public void onUpdatingACL(SharedFolder sf, User user, Role role, boolean suppressAllWarnings)
            throws Exception
    {
        ImmutableCollection<UserID> externalUsers = getExternalUsers(sf);
        boolean isExternalFolder = !externalUsers.isEmpty();

        if (!suppressAllWarnings && isExternalFolder && role.equals(Role.OWNER)) {
            // warn that the new owner will be able to share files with existing external users
            throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(
                    getFullNames(externalUsers));
        }

        // Do not allow editors on externally shared folders
        if (isExternalFolder && role.equals(Role.EDITOR)) {
            throw new ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders(
                    getFullNames(externalUsers));
        }
    }

    /**
     * @return an empty collection if the folder is not shared externally.
     */
    private ImmutableCollection<UserID> getExternalUsers(SharedFolder sf)
            throws SQLException
    {
        ImmutableList.Builder<UserID> builder = ImmutableList.builder();
        for (User user : sf.getAllUsers()) {
            UserID id = user.id();
            if (!id.isTeamServerID() && isExternalUser(id)) builder.add(id);
        }
        return builder.build();
    }

    private boolean isExternalUser(UserID id)
    {
        return !_internalAddresses.matcher(id.getString()).matches();
    }
}
