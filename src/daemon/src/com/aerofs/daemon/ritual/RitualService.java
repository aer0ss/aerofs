package com.aerofs.daemon.ritual;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.linker.event.EITestPauseOrResumeLinker;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.fsi.FSIFile;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.ExitCode;
import com.aerofs.lib.Path;
import com.aerofs.lib.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Ritual.DumpStatsReply;
import com.aerofs.proto.Ritual.ExportFileReply;
import com.aerofs.proto.Ritual.ExportRevisionReply;
import com.aerofs.proto.Ritual.GetACLReply;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.GetPathStatusReply;
import com.aerofs.proto.Ritual.GetSyncStatusReply;
import com.aerofs.proto.Ritual.IRitualService;
import com.aerofs.proto.Ritual.ListExcludedFoldersReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.aerofs.proto.Ritual.ShareFolderReply;
import com.aerofs.proto.Ritual.TestGetObjectIdentifierReply;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

/**
 * For simplicity, the RPC plugin generates only a future-based interface. Users of the RPC plugin
 * (such as Ritual) should implement this interface _asynchronously_.
 *
 * However, the implementation of Ritual is synchronous. The reason is because it was like that in
 * the FSI, and Weihan argued briefly with Greg that there was no need to make them asynchronous for
 * now, as this would require a lot of work. Eventually we will replace synchronous event execution
 * in the Core with Future based methods.
 */
public class RitualService implements IRitualService
{
    private static final Prio PRIO = Prio.HI;

    @Override
    public ListenableFuture<DumpStatsReply> dumpStats(PBDumpStat template)
            throws Exception
    {
        EIDumpStat ev = new EIDumpStat(template, Core.imce());
        ev.execute(PRIO);

        DumpStatsReply reply = DumpStatsReply.newBuilder().setStats(ev.data_()).build();
        return createReply(reply);
    }

    @Override
    public Common.PBException encodeError(Throwable e)
    {
        return Exceptions.toPBWithStackTrace(e);
    }

    @Override
    public ListenableFuture<ShareFolderReply> shareFolder(String user, PBPath path,
            List<PBSubjectRolePair> srps, String emailNote)
            throws Exception
    {
        Map<String, Role> acl = Maps.newTreeMap();
        for (PBSubjectRolePair srp : srps) acl.put(srp.getSubject(), Role.fromPB(srp.getRole()));
        EIShareFolder ev = new EIShareFolder(user, new Path(path), acl, emailNote);
        ev.execute(PRIO);

        ShareFolderReply reply = ShareFolderReply.newBuilder()
                .setShareId(ev._sid.toPB())
                .build();
        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> joinSharedFolder(String user, ByteString shareID, PBPath path)
            throws Exception
    {
        SID sid = new SID(shareID);
        EIJoinSharedFolder ev = new EIJoinSharedFolder(user, new Path(path), sid);
        ev.execute(PRIO);

        return createVoidReply();
    }

    private static PBObjectAttributes toPB(OA oa)
    {
        PBObjectAttributes.Builder bd = PBObjectAttributes.newBuilder()
                .setExcluded(oa.isExpelled());

        switch (oa.type()) {
        case DIR:
            bd.setType(PBObjectAttributes.Type.FOLDER);
            break;
        case ANCHOR:
            bd.setType(PBObjectAttributes.Type.SHARED_FOLDER);
            break;
        case FILE:
            bd.setType(PBObjectAttributes.Type.FILE);
            for (Entry<KIndex, CA> en : oa.cas().entrySet()) {
                bd.addBranch(PBBranch.newBuilder()
                        .setKidx(en.getKey().getInt())
                        .setLength(en.getValue().length()));
            }
            break;
        default: assert false;
        }

        return bd.build();
    }

    @Override
    public ListenableFuture<GetObjectAttributesReply> getObjectAttributes(
            String user, PBPath path) throws Exception
    {
        EIGetAttr ev = new EIGetAttr(user, Core.imce(), new Path(path));
        ev.execute(PRIO);
        if (ev._oa == null) throw new ExNotFound();

        GetObjectAttributesReply reply = GetObjectAttributesReply.newBuilder()
                .setObjectAttributes(toPB(ev._oa))
                .build();
        return createReply(reply);
    }

    @Override
    public ListenableFuture<GetChildrenAttributesReply> getChildrenAttributes(String user,
            PBPath path) throws Exception
    {
        EIGetChildrenAttr ev = new EIGetChildrenAttr(user, new Path(path), Core.imce());
        ev.execute(PRIO);

        GetChildrenAttributesReply.Builder bd = GetChildrenAttributesReply.newBuilder();
        for (OA oa : ev._oas) {
            bd.addChildrenName(oa.name());
            bd.addChildrenAttributes(toPB(oa));
        }

        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<Void> excludeFolder(PBPath path) throws Exception
    {
        EISetExpelled ev = new EISetExpelled(new Path(path), true);
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> includeFolder(PBPath path) throws Exception
    {
        EISetExpelled ev = new EISetExpelled(new Path(path), false);
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListExcludedFoldersReply> listExcludedFolders() throws Exception
    {
        EIListExpelledObjects ev = new EIListExpelledObjects();
        ev.execute(PRIO);

        ListExcludedFoldersReply.Builder bdReply = ListExcludedFoldersReply.newBuilder();
        for (Path path : ev._expelledObjects) bdReply.addPath(path.toPB());
        return createReply(bdReply.build());
    }

    @Override
    public ListenableFuture<ExportFileReply> exportFile(PBPath source) throws Exception
    {
        Path src = new Path(source);
        EIExportFile ev = new EIExportFile(Core.imce(), src);
        ev.execute(PRIO);

        return createReply(ExportFileReply.newBuilder()
                .setDest(ev._dst.getAbsolutePath()).build());
    }

    @Override
    public ListenableFuture<Void> shutdown() throws Exception
    {
        Util.l(this).warn("shutdown requested");
        ExitCode.SHUTDOWN_REQUESTED.exit();

        return createVoidReply();
    }

    /**
     * Helper method to create a Void reply
     */
    private static ListenableFuture<Void> createVoidReply()
    {
        return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
    }

    private static <T> ListenableFuture<T> createReply(T reply)
    {
        return UncancellableFuture.createSucceeded(reply);
    }

    @Override
    public ListenableFuture<ListSharedFoldersReply> listSharedFolders() throws Exception
    {
        EIListSharedFolders ev = new EIListSharedFolders();
        ev.execute(PRIO);
        ListSharedFoldersReply.Builder bd = ListSharedFoldersReply.newBuilder();
        for (Path path : ev._paths) bd.addPath(path.toPB());
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<Void> unshareFolder(String user, PBPath path) throws Exception
    {
        assert false;   // unimplemented
        return null;
    }

    @Override
    public ListenableFuture<GetACLReply> getACL(String user, PBPath path) throws Exception
    {
        EIGetACL ev = new EIGetACL(user, new Path(path), Core.imce());
        ev.execute(PRIO);

        // we only get here if the event suceeded properly

        GetACLReply.Builder replyBuilder = GetACLReply.newBuilder();
        for (Entry<String, Role> en : ev._subject2role.entrySet()) {
            replyBuilder.addSubjectRole(PBSubjectRolePair.newBuilder()
                    .setSubject(en.getKey())
                    .setRole(en.getValue().toPB()));
        }

        return createReply(replyBuilder.build());
    }

    @Override
    public ListenableFuture<Void> updateACL(String user, PBPath path, List<PBSubjectRolePair> srps)
            throws Exception
    {
        Map<String, Role> map = Maps.newTreeMap();
        for (PBSubjectRolePair srp : srps) map.put(srp.getSubject(), Role.fromPB(srp.getRole()));

        EIUpdateACL ev = new EIUpdateACL(user, new Path(path), map, Core.imce());
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(String user, PBPath path, List<String> subjects)
            throws Exception
    {
        EIDeleteACL ev = new EIDeleteACL(user, new Path(path), subjects, Core.imce());
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<ListRevChildrenReply> listRevChildren(PBPath path) throws Exception
    {
        EIListRevChildren ev = new EIListRevChildren(new Path(path), Core.imce());
        ev.execute(PRIO);
        ListRevChildrenReply.Builder bd = ListRevChildrenReply.newBuilder();
        for (Child child : ev.getChildren()) bd.addChild(child.toPB());
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<ListRevHistoryReply> listRevHistory(PBPath path) throws Exception
    {
        EIListRevHistory ev = new EIListRevHistory(new Path(path), Core.imce());
        ev.execute(PRIO);
        ListRevHistoryReply.Builder bd = ListRevHistoryReply.newBuilder();
        for (Revision rev : ev.getRevisions()) bd.addRevision(rev.toPB());
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<ExportRevisionReply> exportRevision(PBPath path, ByteString index) throws Exception
    {
        EIExportRevision ev = new EIExportRevision(Core.imce(), new Path(path), index.toByteArray());
        ev.execute(PRIO);
        return createReply(ExportRevisionReply.newBuilder()
                .setDest(ev._dst.getAbsolutePath()).build());
    }

    @Override
    public ListenableFuture<Void> logThreads()
            throws Exception
    {
        Util.logAllThreadStackTraces();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetSyncStatusReply> getSyncStatus(PBPath path) throws Exception
    {
        EIGetSyncStatus ev = new EIGetSyncStatus(new Path(path), Core.imce());
        ev.execute(PRIO);
        GetSyncStatusReply.Builder bd = GetSyncStatusReply.newBuilder();
        bd.setIsServerUp(ev._isServerUp);
        for (PBSyncStatus dss : ev._peers) bd.addStatusList(dss);
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<GetPathStatusReply> getPathStatus(List<PBPath> pbPaths)
            throws Exception
    {
        List<Path> pathList = Lists.newArrayList();
        for (PBPath pbPath : pbPaths) pathList.add(new Path(pbPath));
        EIGetStatusOverview ev = new EIGetStatusOverview(pathList, Core.imce());
        ev.execute(PRIO);
        GetPathStatusReply.Builder bd = GetPathStatusReply.newBuilder();
        bd.addAllStatus(ev._statusOverviews);
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<TestGetObjectIdentifierReply> testGetObjectIdentifier(PBPath path)
            throws Exception
    {
        EIGetAttr ev = new EIGetAttr(Cfg.user(), Core.imce(), new Path(path));
        ev.execute(PRIO);
        if (ev._oa == null) throw new ExNotFound();

        TestGetObjectIdentifierReply reply = TestGetObjectIdentifierReply.newBuilder()
                .setSidx(ev._oa.soid().sidx().getInt())
                .setOid(ev._oa.soid().oid().toPB())
                .build();

        return createReply(reply);
    }

    @Override
    public ListenableFuture<Void> testPauseLinker() throws Exception
    {
        new EITestPauseOrResumeLinker(true).execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> testResumeLinker() throws Exception
    {
        new EITestPauseOrResumeLinker(false).execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> reloadConfig() throws Exception
    {
        new EIReloadConfig().execute(FSIFile.PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> pauseSyncing() throws Exception
    {
        new EIPauseOrResumeSyncing(true, Core.imce()).execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> resumeSyncing() throws Exception
    {
        new EIPauseOrResumeSyncing(false, Core.imce()).execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetActivitiesReply> getActivities(Boolean brief, Integer maxResults,
            Long pageToken) throws Exception
    {
        EIGetActivities ev = new EIGetActivities(brief, pageToken, maxResults, Core.imce());
        ev.execute(PRIO);
        GetActivitiesReply.Builder bdReply = GetActivitiesReply.newBuilder()
                .addAllActivity(ev._activities)
                .setHasUnresolvedDevices(ev._hasUnresolvedDevices);
        if (ev._replyPageToken != null) bdReply.setPageToken(ev._replyPageToken);
        return createReply(bdReply.build());
    }

    @Override
    public ListenableFuture<Void> relocate(String newAbsRootAnchor) throws Exception
    {
        new EIRelocateRootAnchor(newAbsRootAnchor, Core.imce()).execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> heartbeat() throws Exception
    {
        new EIHeartbeat(Core.imce()).execute(PRIO);
        return createVoidReply();
    }

}
