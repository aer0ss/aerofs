/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.gui.sharing.members.SharedFolderMember.Factory;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBGroupPermissions;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.common.SharedFolderState;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static java.util.stream.Collectors.toList;

public class SharingModel
{
    private final IRitualClientProvider             _ritualProvider;
    private final InjectableSPBlockingClientFactory _spClientFactory;
    private final Factory                           _factory;
    private final ListeningExecutorService          _executor;

    public SharingModel(IRitualClientProvider ritualProvider,
            InjectableSPBlockingClientFactory spClientFactory, Factory factory,
            ListeningExecutorService executor)
    {
        _ritualProvider     = ritualProvider;
        _spClientFactory    = spClientFactory;
        _factory            = factory;
        _executor           = executor;
    }

    public ListenableFuture<LoadResult> load(Path path)
    {
        return _executor.submit(() -> {
            SID sid = getSIDImpl(path);

            List<SharedFolderMember> members = getSharedFolderMembersImpl(sid).stream()
                    .filter(member -> member._state != SharedFolderState.LEFT)
                    .collect(toList());

            // N.B. the local user may not be a member, e.g. Team Server
            // N.B. with addition of groups, the local user may not be a direct member of the
            //   shared folder; the local user could be a member of a group which is then the
            //   member of the shared folder.
            Permissions localUserPermissions = members.stream()
                    .filter(SharedFolderMember::isLocalUser)
                    .findAny()
                    .map(member -> member._permissions)
                    .orElse(null);
            return new LoadResult(sid, members, localUserPermissions);
        });
    }

    // makes a ritual call to determine the SID for a given path to a shared folder.
    private SID getSIDImpl(Path path)
            throws Exception
    {
        return _ritualProvider.getBlockingClient().listSharedFolders()
                .getSharedFolderList().stream()
                .filter(folder -> path.equals(Path.fromPB(folder.getPath())))
                .findAny()
                .map(folder -> new SID(folder.getStoreId()))
                .orElseThrow(() -> new ExBadArgs("Invalid shared folder."));
    }

    // make a SP call to retrieve the shared folder members
    private List<SharedFolderMember> getSharedFolderMembersImpl(SID sid)
            throws Exception
    {
        List<PBSharedFolder> sharedFolders = _spClientFactory.create()
                .signInRemote()
                .listSharedFolders(ImmutableList.of(sid.toPB()))
                .getSharedFolderList();
        // assert the contract of the sp call
        checkArgument(sharedFolders.size() == 1);

        PBSharedFolder pbFolder = sharedFolders.get(0);

        int count = pbFolder.getUserPermissionsAndStateCount() +
                (L.isGroupSharingReady() ? pbFolder.getGroupPermissionsCount() : 0);
        List<SharedFolderMember> members = newArrayListWithExpectedSize(count);

        for (PBUserPermissionsAndState ups : pbFolder.getUserPermissionsAndStateList()) {
            members.add(_factory.fromPB(ups));
        }

        if (L.isGroupSharingReady()) {
            for (PBGroupPermissions gp : pbFolder.getGroupPermissionsList()) {
                members.add(_factory.fromPB(gp));
            }
        }

        return members;
    }

    public ListenableFuture<Void> setRole(SID sid, SharedFolderMember member,
            Permissions permissions, boolean suppressSharedFolderRulesWarnings)
    {
        return _executor.submit(() -> {
            SPBlockingClient sp = _spClientFactory.create().signInRemote();

            if (permissions == null) {
                sp.deleteACL(sid.toPB(), member.getSubject());
            } else {
                sp.updateACL(sid.toPB(), member.getSubject(), permissions.toPB(),
                        suppressSharedFolderRulesWarnings);
            }

            return null;
        });
    }

    public class LoadResult
    {
        public final SID                        _sid;
        public final List<SharedFolderMember>   _members;
        public final Permissions                _permissions;

        public LoadResult(SID sid, List<SharedFolderMember> members, Permissions permissions)
        {
            _sid            = sid;
            _members        = members;
            _permissions    = permissions;
        }
    }
}
