/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.sharing_rules;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolder.UserPermissionsAndState;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription.Type;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 *  BloomBerg core requirement:
 *      avoid data leaks
 *
 *  high-level rule:
 *      internal users may not write to externally shared folders
 *      external users may not manage shared folders
 *
 *  user-visible behavior:
 *      - internal users see a warning when inviting an external user
 *      - internal users lose write access to folder as soon as an external user is invited
 *      - attempts to invite an internal user as EDITOR (i.e. WRITE, no MANAGE) will warn
 *        that the actual permission granted will be VIEWER only
 *      - attempts to invite an internal user as OWNER (i.e. WRITE and MANAGE) will warn
 *        that the actual permission granted will be MANAGE only
 *      - attempts to invite an external user as OWNER (i.e. WRITE and MANAGE) will warn
 *        that the actual permission granted will be EDITOR only
 *
 * NB: this class is a short term solution
 *
 * The long term solution will probably be to offer fine-grained group memberships and a DSL to
 * let customers enforce their own specific sharing rules based on membership tests.
 */
public class RestrictedExternalSharing implements ISharingRules
{
    private final SharingRulesFactory _f;

    private final User _sharer;
    private final List<DetailedDescription> _warnings = Lists.newArrayList();

    private boolean _shouldBumpEpoch;

    public RestrictedExternalSharing(SharingRulesFactory f, User sharer)
    {
        _f = f;
        _sharer = sharer;
    }

    @Override
    public Permissions onUpdatingACL(SharedFolder sf, User sharee, Permissions newPermissions)
            throws Exception
    {
        ImmutableCollection<UserID> externalMembers = getExternalUsers(sf);

        boolean isExternalFolder = !externalMembers.isEmpty();
        boolean isExternalSharee = !_f._authenticator.isInternalUser(sharee.id());

        if (isExternalSharee && !externalMembers.contains(sharee.id())) {
            // adding an external user
            _warnings.add(new DetailedDescription(Type.WARNING_EXTERNAL_SHARING,
                    getFullNames(ImmutableList.of(sharee.id()))));

            if (!isExternalFolder) revokeWritePermissionForInternalUsers(sf, _sharer);
        }

        // prevent granting write access to an externally shared folder to internal users
        if (isExternalFolder
                && !isExternalSharee
                && !sharee.isWhitelisted()
                && newPermissions.covers(Permission.WRITE))
        {
            _warnings.add(new DetailedDescription(Type.WARNING_DOWNGRADE,
                    getFullNames(externalMembers)));
            return newPermissions.minus(Permission.WRITE);
        }
        if (isExternalSharee
                && newPermissions.covers(Permission.MANAGE))
        {
            _warnings.add(new DetailedDescription(Type.WARNING_NO_EXTERNAL_OWNERS,
                    getFullNames(ImmutableList.of(sharee.id()))));
            return newPermissions.minus(Permission.MANAGE);
        }

        return newPermissions;
    }

    @Override
    public Permissions onUpdatingACL(SharedFolder sf, Group sharee, Permissions newPermissions)
            throws Exception
    {
        ImmutableCollection<UserID> externalMembers = getExternalUsers(sf);
        boolean hasInternal = false;
        boolean addingExternal = false;
        boolean isExternalFolder = !externalMembers.isEmpty();
        List <UserID> externalUsers = Lists.newLinkedList();

        for (User u : sharee.listMembers()) {
            if (!_f._authenticator.isInternalUser(u.id())) {
                externalUsers.add(u.id());
                if (sf.getStateNullable(u) == null) {
                    addingExternal = true;
                }
            } else if (!u.isWhitelisted()) {
                hasInternal = true;
            }
        }

        if (addingExternal) {
            if (_f._authenticator.isInternalUser(_sharer.id())) {
                _warnings.add(new DetailedDescription(Type.WARNING_EXTERNAL_SHARING,
                        getFullNames(ImmutableList.copyOf(externalUsers))));
            }

            if (!isExternalFolder) revokeWritePermissionForInternalUsers(sf, _sharer);
        }

        // prevent granting write access to an externally shared folder to internal users
        if ((isExternalFolder || addingExternal)
                && hasInternal
                && newPermissions.covers(Permission.WRITE))
        {
            _warnings.add(new DetailedDescription(Type.WARNING_DOWNGRADE,
                    getFullNames(externalMembers)));
            newPermissions = newPermissions.minus(Permission.WRITE);
        }

        if (!externalUsers.isEmpty()
                && newPermissions.covers(Permission.MANAGE))
        {
            _warnings.add(new DetailedDescription(Type.WARNING_NO_EXTERNAL_OWNERS,
                    getFullNames(externalMembers)));
            newPermissions = newPermissions.minus(Permission.MANAGE);
        }

        return newPermissions;
    }

    @Override
    public void throwIfAnyWarningTriggered() throws ExExternalServiceUnavailable, ExSharingRulesWarning
    {
        if (!_warnings.isEmpty()) throw new ExSharingRulesWarning(_warnings);
    }

    @Override
    public boolean shouldBumpEpoch()
    {
        return _shouldBumpEpoch;
    }

    private ImmutableMap<UserID, FullName> getFullNames(ImmutableCollection<UserID> userIDs)
            throws SQLException, ExNotFound
    {
        ImmutableMap.Builder<UserID, FullName> builder = ImmutableMap.builder();
        for (UserID userID : userIDs) {
            User user = _f._factUser.create(userID);
            // Use empty names if the user hasn't signed up.
            builder.put(userID, user.exists() ? user.getFullName() : new FullName("", ""));
        }
        return builder.build();
    }

    /**
     * @return an empty collection if the folder is not shared externally.
     */
    private ImmutableCollection<UserID> getExternalUsers(SharedFolder sf)
            throws SQLException, ExExternalServiceUnavailable
    {
        ImmutableList.Builder<UserID> builder = ImmutableList.builder();
        for (User user : sf.getAllUsers()) {
            UserID id = user.id();
            if (!_f._authenticator.isInternalUser(id)) builder.add(id);
        }
        return builder.build();
    }

    private void revokeWritePermissionForInternalUsers(SharedFolder sf, User sharer)
            throws SQLException, ExNoAdminOrOwner, ExNotFound, IOException, MessagingException
    {
        for (UserPermissionsAndState urs : sf.getAllUsersRolesAndStates()) {
            if (urs._user.id().isTeamServerID() || urs._user.isWhitelisted()) continue;
            if (urs._permissions.covers(Permission.WRITE)) {
                _shouldBumpEpoch = true;
                sf.revokePermission(urs._user, Permission.WRITE);
                // Notify the user about the role change
                _f._sfnEmailer.sendRoleChangedNotificationEmail(sf, sharer, urs._user,
                        urs._permissions, urs._permissions.minus(Permission.WRITE));
            }
        }
    }
}
