/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.invitee;

import com.aerofs.base.LazyChecked;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.ListOrganizationMembersReply.PBUserAndLevel;
import com.aerofs.proto.Sp.PBGroup;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * InviteModel is a collection of methods to provide backend support to the invite user dialog.
 */
public class InviteModel
{
    private static final Logger l = Loggers.getLogger(InviteModel.class);

    private final InjectableCfg                         _cfg;
    private final InjectableSPBlockingClientFactory     _spClientFactory;
    private final IRitualClientProvider                 _ritualProvider;
    private final ListeningExecutorService              _executor;
    private LazyChecked<SPBlockingClient, Exception>    _spClient;

    public InviteModel(InjectableCfg cfg, InjectableSPBlockingClientFactory spClientFactory,
            IRitualClientProvider ritualProvider, ListeningExecutorService executor)
    {
        _cfg                = cfg;
        _spClientFactory    = spClientFactory;
        _ritualProvider     = ritualProvider;
        _executor           = executor;

        resetSPClient();
    }

    private void resetSPClient()
    {
        _spClient = new LazyChecked<>(() -> _spClientFactory.create().signInRemote());
    }

    // this method is a misfit, but we need this on the invite dialog.
    public ListenableFuture<String> getLocalUserFirstName()
    {
        return _executor.submit(() -> _spClient.get()
                .getUserPreferences(_cfg.did().toPB())
                .getFirstName());
    }

    // the future will throw CancellationException if the task was cancelled
    public ListenableFuture<List<Invitee>> query(String query)
    {
        if (isBlank(query)) {
            return immediateFuture(emptyList());
        } else {
            return _executor.submit(() -> queryImplRetryOnce(query));
        }
    }

    private List<Invitee> queryImplRetryOnce(String query)
            throws Exception
    {
        try {
            return queryImpl(query);
        } catch (InterruptedException e) {
            // this is likely caused by broken connection, reset the cached client and try again
            resetSPClient();
            return queryImpl(query);
        }
    }

    private List<Invitee> queryImpl(String query)
            throws Exception
    {
        List<Invitee> result = newArrayList();
        // groups before users
        result.addAll(queryGroups(query));
        result.addAll(queryUsers(query));
        // return the first 6 results at most
        return result.subList(0, Math.min(6, result.size()));
    }

    private List<Invitee> queryUsers(String query)
            throws Exception
    {
        return _spClient.get()
                .listOrganizationMembers(6, 0, query)
                .getUserAndLevelList().stream()
                .map(PBUserAndLevel::getUser)
                .map(this::createUserInviteeFromPBNullable)
                .filter(invitee -> invitee != null)
                .collect(toList());
    }

    private List<Invitee> queryGroups(String query)
            throws Exception
    {
        if (!L.isGroupSharingEnabled()) {
            return emptyList();
        }

        return _spClient.get()
                .listGroups(6, 0, query)
                .getGroupsList().stream()
                .map(this::createGroupInviteeFromPBNullable)
                .filter(invitee -> invitee != null)
                .collect(toList());
    }

    public ListenableFuture<Void> invite(Path path, List<Invitee> invitees, Permissions permissions,
            String note, boolean suppressSharedFolderRulesWarnings)
    {
        return _executor.submit(() -> {
            // FIXME (AT): support sharing with groups.
            // this is currently broken when group sharing is enabled as the GUI allows the user
            // to select group suggestions but the GUI will then filter out groups.
            List<PBSubjectPermissions> pbsps = invitees.stream()
                    .filter(invitee -> invitee._type == InviteeType.USER)
                    .map(invitee -> new SubjectPermissions((UserID)invitee._value, permissions))
                    .map(SubjectPermissions::toPB)
                    .collect(toList());
            _ritualProvider.getBlockingClient()
                    .shareFolder(path.toPB(), pbsps, note, suppressSharedFolderRulesWarnings);
            return null;
        });
    }

    public Invitee createInvitee(String text)
    {
        try {
            if (!Util.isValidEmailAddress(text)) {
                return createInvalidInvitee(text);
            }
            return createUserInvitee(UserID.fromExternal(text), "", "");
        } catch (Exception e) {
            return createInvalidInvitee(text);
        }
    }

    private Invitee createUserInvitee(UserID userID, String firstname, String lastname)
    {
        String fullname = (firstname + " " + lastname).trim();
        String longText = isBlank(fullname)
                ? userID.getString()
                : fullname + " (" + userID.getString() + ")";
        return new Invitee(InviteeType.USER, userID,
                isBlank(fullname) ? userID.getString() : fullname, longText);
    }

    private Invitee createGroupInvitee(GroupID groupID, String name)
    {
        return new Invitee(InviteeType.GROUP, groupID, name, name);
    }

    private Invitee createInvalidInvitee(String text)
    {
        text = text.trim();
        return new Invitee(InviteeType.INVALID, text, text, text);
    }

    // returns null on invalid user
    private @Nullable Invitee createUserInviteeFromPBNullable(PBUser pb)
    {
        UserID userID;
        try {
            userID = UserID.fromExternal(pb.getUserEmail());
        } catch (ExEmptyEmailAddress e) {
            l.warn("SP returned an invalid user suggestion."); // ignored
            return null;
        }
        return createUserInvitee(userID, pb.getFirstName(), pb.getLastName());
    }

    // returns null on invalid group
    private @Nullable Invitee createGroupInviteeFromPBNullable(PBGroup pb)
    {
        GroupID groupID;
        try {
            groupID = GroupID.fromExternal(pb.getGroupId());
        } catch (ExBadArgs e) {
            l.warn("SP returned an invalid group suggestion."); // ignored
            return null;
        }
        return createGroupInvitee(groupID, pb.getCommonName());
    }

    public enum InviteeType
    {
        USER, GROUP, INVALID
    }

    public class Invitee
    {
        // TODO (AT): not a fan of using _type & untyped _value, suggestions welcomed
        public final InviteeType _type;
        // _value is either an UserID for users, GroupID for groups, or String for invalid.
        public final Object _value;
        public final String _shortText;
        public final String _longText;

        public Invitee(InviteeType type, Object value, String shortText, String longText)
        {
            _type = type;
            _value = value;
            _shortText = shortText;
            _longText = longText;
        }
    }
}
