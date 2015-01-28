/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.LazyChecked;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.defects.Defect;
import com.aerofs.defects.Defects;
import com.aerofs.gui.sharing.SharedFolderMember.*;
import com.aerofs.gui.sharing.Subject.*;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.ListGroupStatusInSharedFolderReply.PBUserAndState;
import com.aerofs.proto.Sp.ListOrganizationMembersReply.PBUserAndLevel;
import com.aerofs.proto.Sp.PBGroup;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBGroupPermissions;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.common.SharedFolderState;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isBlank;

public class SharingModel
{
    private final InjectableCfg                             _cfg;
    private final IRitualClientProvider                     _ritualProvider;
    private final InjectableSPBlockingClientFactory         _spClientFactory;
    private final LazyChecked<SPBlockingClient, Exception>  _spClient;
    public  final Factory                                   _factory;
    private final ListeningExecutorService                  _executor;

    public SharingModel(InjectableCfg cfg, IRitualClientProvider ritualProvider,
            InjectableSPBlockingClientFactory spClientFactory, ListeningExecutorService executor)
    {
        _cfg                = cfg;
        _ritualProvider     = ritualProvider;
        _spClientFactory    = spClientFactory;
        _spClient           = new LazyChecked<>(() -> _spClientFactory.create().signInRemote());
        _factory            = new Factory(_cfg);
        _executor           = executor;
    }

    public ListenableFuture<String> getLocalUserFirstname()
    {
        return _executor.submit(() -> _spClient.get()
                .getUserPreferences(_cfg.did().toPB())
                .getFirstName());
    }

    public ListenableFuture<List<Subject>> getSuggestions(String query)
    {
        if (isBlank(query)) return immediateFuture(emptyList());

        return _executor.submit(() -> getSuggestionsImpl(query));
    }

    private List<Subject> getSuggestionsImpl(String query)
            throws Exception
    {
        ElapsedTimer timer = new ElapsedTimer().start();

        List<Subject> suggestions = newArrayList();
        // groups before users
        suggestions.addAll(getGroupSuggestions(query));
        suggestions.addAll(getUserSuggestions(query));

        Defects.newMetric("gui.sharing.suggestions")
                .addData("count", Math.min(suggestions.size(), 6))
                .addData("elapsed_time", timer.elapsed())
                .sendAsync();

        // only include the first 6 suggestions at most
        return suggestions.subList(0, Math.min(6, suggestions.size()));
    }

    private List<User> getUserSuggestions(String query)
            throws Exception
    {
        return _spClient.get()
                .listOrganizationMembers(6, 0, query)
                .getUserAndLevelList().stream()
                .map(PBUserAndLevel::getUser)
                .map(_factory::fromPBNullable)
                .filter(user -> user != null)
                .collect(toList());
    }

    private List<Group> getGroupSuggestions(String query)
            throws Exception
    {
        return _spClient.get().listGroups(6, 0, query)
                .getGroupsList().stream()
                .map(_factory::fromPBNullable)
                .filter(group -> group != null)
                .collect(toList());
    }

    public ListenableFuture<Void> invite(Path path, List<Subject> subjects, Permissions permissions,
            String note, boolean suppressSharedFolderRulesWarnings)
    {
        return _executor.submit(() -> {
            _ritualProvider.getBlockingClient().shareFolder(path.toPB(), subjects.stream()
                            .map(subject -> PBSubjectPermissions.newBuilder()
                                    .setSubject(subject.toPB())
                                    .setPermissions(permissions.toPB())
                                    .build())
                            .collect(toList()), note, suppressSharedFolderRulesWarnings);
            return null;
        });
    }

    public ListenableFuture<MemberListResult> load(Path path)
    {
        return _executor.submit(() -> {
            SID sid = getSIDImpl(path);

            List<SharedFolderMember> members = getSharedFolderMembersImpl(sid).stream()
                    .filter(member -> member.getState() != SharedFolderState.LEFT)
                    .collect(toList());

            Permissions permissions = getLocalUserPermissions(members);

            return new MemberListResult(sid, members, permissions);
        });
    }

    private SID getSIDImpl(Path path)
            throws Exception
    {
        return _ritualProvider.getBlockingClient().listSharedFolders()
                .getSharedFolderList().stream()
                .filter(folder -> path.equals(Path.fromPB(folder.getPath())))
                .findAny()
                .map(folder -> new SID(folder.getStoreId()))
                .orElseThrow(() -> new ExNotFound("Shared folder not found."));
    }

    private List<SharedFolderMember> getSharedFolderMembersImpl(SID sid)
            throws Exception
    {
        Defect metric = Defects.newMetric("gui.sharing.members");
        ElapsedTimer timer = new ElapsedTimer().start();

        List<PBSharedFolder> pbSharedFolders = _spClient.get()
                .listSharedFolders(ImmutableList.of(sid.toPB()))
                .getSharedFolderList();

        // assert the contract of the sp call is upheld
        checkArgument(pbSharedFolders.size() == 1);

        PBSharedFolder pbSharedFolder = pbSharedFolders.get(0);

        metric.addData("user_count", pbSharedFolder.getUserPermissionsAndStateCount());

        List<SharedFolderMember> members = newArrayList();

        for (PBUserPermissionsAndState pbups : pbSharedFolder.getUserPermissionsAndStateList()) {
            members.add(_factory.fromPB(pbups));
        }

        metric.addData("group_count", pbSharedFolder.getGroupPermissionsCount());

        for (PBGroupPermissions pbgp : pbSharedFolder.getGroupPermissionsList()) {
            GroupPermissions gp = _factory.fromPB(pbgp);
            List<PBUserAndState> pbuss = _spClient.get()
                    .listGroupStatusInSharedFolder(gp._group._groupID.getInt(), sid.toPB())
                    .getUserAndStateList();

            members.add(gp);
            for (PBUserAndState pbus : pbuss) {
                GroupPermissionUserAndState member = _factory.fromPB(gp, pbus);
                members.add(member);
                gp._children.add(member);
            }
        }

        metric.addData("total_count", members.size())
                .addData("elapsed_time", timer.elapsed())
                .sendAsync();

        return members;
    }

    private Permissions getLocalUserPermissions(List<SharedFolderMember> members)
    {
        return members.stream()
                .filter(member -> member.getSubject().isLocalUser())
                .map(SharedFolderMember::getPermissions)
                .reduce(Permissions.allOf(), Permissions::union);
    }

    public ListenableFuture<Void> setSubjectPermissions(SID sid, Subject subject,
            Permissions permissions, boolean suppressSharedFolderRulesWarnings)
    {
        return _executor.submit(() -> {
            _spClient.get().updateACL(sid.toPB(), subject.toPB(), permissions.toPB(),
                    suppressSharedFolderRulesWarnings);
            return null;
        });
    }

    public ListenableFuture<Void> removeSubject(SID sid, Subject subject)
    {
        return _executor.submit(() -> {
            _spClient.get().deleteACL(sid.toPB(), subject.toPB());
            return null;
        });
    }

    public static class Factory
    {
        private final InjectableCfg _cfg;

        public Factory(InjectableCfg cfg)
        {
            _cfg = cfg;
        }

        public Subject fromUserInput(String text)
        {
            try {
                if (!Util.isValidEmailAddress(text)) {
                    return new InvalidSubject(text);
                }
                return new User(UserID.fromExternal(text), "", "", _cfg);
            } catch (Exception e) {
                return new InvalidSubject(text);
            }
        }

        private User fromPB(PBUser pb)
                throws ExEmptyEmailAddress, ExBadArgs
        {
            return new User(UserID.fromExternal(pb.getUserEmail()), pb.getFirstName(),
                    pb.getLastName(), _cfg);
        }

        // returns null on error
        private  @Nullable User fromPBNullable(PBUser pb)
        {
            try {
                return fromPB(pb);
            } catch (ExEmptyEmailAddress | ExBadArgs e) {
                return null;
            }
        }

        private Group fromPB(PBGroup pb)
                throws ExBadArgs
        {
            return new Group(GroupID.fromExternal(pb.getGroupId()), pb.getCommonName());
        }

        // returns null on error
        private @Nullable Group fromPBNullable(PBGroup pb)
        {
            try {
                return fromPB(pb);
            } catch (ExBadArgs e) {
                return null;
            }
        }

        private UserPermissionsAndState fromPB(PBUserPermissionsAndState pb)
                throws ExEmptyEmailAddress, ExBadArgs
        {
            return new UserPermissionsAndState(fromPB(pb.getUser()),
                    Permissions.fromPB(pb.getPermissions()),
                    SharedFolderState.fromPB(pb.getState()));
        }

        private GroupPermissions fromPB(PBGroupPermissions pb)
                throws ExBadArgs
        {
            return new GroupPermissions(fromPB(pb.getGroup()),
                    Permissions.fromPB(pb.getPermissions()));
        }

        private GroupPermissionUserAndState fromPB(GroupPermissions groupPermissions,
                PBUserAndState pb)
                throws ExEmptyEmailAddress, ExBadArgs
        {
            return new GroupPermissionUserAndState(groupPermissions, fromPB(pb.getUser()),
                    SharedFolderState.fromPB(pb.getState()));
        }
    }

    public class MemberListResult
    {
        public final SID                        _sid;
        public final List<SharedFolderMember>   _members;
        public final Permissions                _permissions;

        public MemberListResult(SID sid, List<SharedFolderMember> members, Permissions permissions)
        {
            _sid            = sid;
            _members        = members;
            _permissions    = permissions;
        }
    }
}
