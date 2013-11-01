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
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningAddExternalUser;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
import com.aerofs.sp.common.UserFilter;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * This rule is to fulfill Bloomberg's requirements which is listed below. See
 * TestSP_ReadOnlyExternalFolderRules for a formal spec.
 *
 * o When adding external users to a shared folder, either through Desktop or Web UI, the system:
 * - show the warning: "1) be careful when sharing files with external parties. 2) ensure that all
 * existing files in the folder have nothing confidential. 3) all existing editors of the folder
 * will be automatically converted to viewers."
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
    private final User.Factory _factUser;
    private final UserFilter _filter;
    private final SharedFolderNotificationEmailer _sfnEmailer;

    public ReadOnlyExternalFolderRules(UserFilter filter, User.Factory factUser,
            SharedFolderNotificationEmailer sfnEmailer)
    {
        _filter = filter;
        _factUser = factUser;
        _sfnEmailer = sfnEmailer;
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

        // check enforcement _before_ showing warnings to the user (see the above line), so the user
        // doesn't see a warning message and then an error message. See also onUpdatingACL().
        if (wasExternal || convertToExternal) throwIfInvitingEditors(srps, allExternal);

        // show warning messages only if the sharer is an internal user
        if ((!suppressAllWarnings) && _filter.isInternalUser(sharer.id())) {
            showWarningsForExternalFolders(wasExternal, srps, newExternal, allExternal);
        }

        ImmutableCollection<UserID> users;
        if (convertToExternal) users = convertEditorsToViewers(sf, sharer);
        else users = ImmutableList.of();

        return users;
    }

    private ImmutableCollection<UserID> getExternalUsers(List<SubjectRolePair> srps)
    {
        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();
        for (SubjectRolePair srp : srps) {
            if (!_filter.isInternalUser(srp._subject)) builder.add(srp._subject);
        }
        return builder.build();
    }

    // convert existing editors to viewers, skip Team Servers
    private ImmutableCollection<UserID> convertEditorsToViewers(SharedFolder sf, User sharer)
            throws SQLException, ExNoAdminOrOwner, ExNotFound, IOException, MessagingException
    {
        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();
        for (User user : sf.getAllUsers()) {
            if (user.id().isTeamServerID()) continue;
            if (sf.getRoleNullable(user) == Role.EDITOR) {
                builder.addAll(sf.setRole(user, Role.VIEWER));
                // Notify the user about the role change
                _sfnEmailer.sendRoleChangedNotificationEmail(sf, sharer, user, Role.EDITOR,
                        Role.VIEWER);
            }
        }
        return builder.build();
    }

    private void showWarningsForExternalFolders(boolean wasExternal, List<SubjectRolePair> newUsers,
            ImmutableCollection<UserID> newExternalUsers, ImmutableCollection<UserID> allExternalUsers)
            throws Exception
    {
        if (!newExternalUsers.isEmpty()) {
            // warn that external users will be added
            throw new ExSharedFolderRulesWarningAddExternalUser();
        }

        if (wasExternal) {
            // if there is an owner in the invitation list, warn that the owners will be able to
            // share files with existing external users
            for (SubjectRolePair srp : newUsers) {
                if (srp._role.equals(Role.OWNER)) {
                    throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(
                            getFullNames(allExternalUsers));
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

    private void throwIfInvitingEditors(List<SubjectRolePair> newUsers,
            ImmutableCollection<UserID> allExternalUsers)
            throws Exception
    {
        for (SubjectRolePair srp : newUsers) {
            if (srp._role.equals(Role.EDITOR)) {
                throw new ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders(
                        getFullNames(allExternalUsers));
            }
        }
    }

    @Override
    public void onUpdatingACL(SharedFolder sf, Role role, boolean suppressAllWarnings)
            throws Exception
    {
        ImmutableCollection<UserID> externalUsers = getExternalUsers(sf);
        boolean isExternalFolder = !externalUsers.isEmpty();

        if (role.equals(Role.OWNER) && isExternalFolder && !suppressAllWarnings) {
            // warn that the new owner will be able to share files with existing external users
            throw new ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(
                    getFullNames(externalUsers));
        }

        // Do not allow editors on externally shared folders
        if (role.equals(Role.EDITOR) && isExternalFolder) {
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
            if (!_filter.isInternalUser(id)) builder.add(id);
        }
        return builder.build();
    }

}
