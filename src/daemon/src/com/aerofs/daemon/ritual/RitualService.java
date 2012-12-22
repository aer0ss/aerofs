package com.aerofs.daemon.ritual;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.linker.event.EITestPauseOrResumeLinker;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.admin.EIExportConflict;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.admin.EITestMultiuserJoinRootStore;
import com.aerofs.daemon.event.admin.EITransportFlood;
import com.aerofs.daemon.event.admin.EITransportFloodQuery;
import com.aerofs.daemon.event.admin.EITransportPing;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.fs.EICreateObject;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.fs.EIImportFile;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Ritual.DumpStatsReply;
import com.aerofs.proto.Ritual.ExportConflictReply;
import com.aerofs.proto.Ritual.ExportFileReply;
import com.aerofs.proto.Ritual.ExportRevisionReply;
import com.aerofs.proto.Ritual.GetACLReply;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.GetPathStatusReply;
import com.aerofs.proto.Ritual.GetSyncStatusReply;
import com.aerofs.proto.Ritual.IRitualService;
import com.aerofs.proto.Ritual.ListConflictsReply;
import com.aerofs.proto.Ritual.ListExcludedFoldersReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.aerofs.proto.Ritual.TestGetObjectIdentifierReply;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Ritual.TransportFloodQueryReply;
import com.aerofs.proto.Ritual.TransportPingReply;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import javax.annotation.Nullable;

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
    public ListenableFuture<Void> shareFolder(PBPath path, List<PBSubjectRolePair> srps,
            String emailNote)
            throws Exception
    {
        Map<UserID, Role> acl = Maps.newTreeMap();
        for (PBSubjectRolePair srp : srps) {
            acl.put(UserID.fromExternal(srp.getSubject()), Role.fromPB(srp.getRole()));
        }
        EIShareFolder ev = new EIShareFolder(Cfg.user(), new Path(path), acl,
                emailNote);
        ev.execute(PRIO);

        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> joinSharedFolder(String code)
            throws Exception
    {
        EIJoinSharedFolder ev = new EIJoinSharedFolder(code);
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
                        .setLength(en.getValue().length())
                        .setMtime(en.getValue().mtime()));
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
        EIGetAttr ev = new EIGetAttr(UserID.fromExternal(user), Core.imce(), new Path(path));
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
        EIGetChildrenAttr ev = new EIGetChildrenAttr(UserID.fromExternal(user), new Path(path),
                Core.imce());
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
    public ListenableFuture<Void> importFile(PBPath destination, String source) throws Exception
    {
        EIImportFile ev = new EIImportFile(new Path(destination), source, Core.imce());
        ev.execute(PRIO);
        return createVoidReply();
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
    public ListenableFuture<Void> createObject(PBPath path, Boolean dir) throws Exception
    {
        EICreateObject ev = new EICreateObject(Cfg.user(), Core.imce(), new Path(path), dir);
        ev.execute(PRIO);
        if (ev._exist) throw new ExAlreadyExist();
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteObject(PBPath path) throws Exception
    {
        EIDeleteObject ev = new EIDeleteObject(Cfg.user(), Core.imce(), new Path(path));
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> moveObject(PBPath pathFrom, PBPath pathTo) throws Exception
    {
        Path to = new Path(pathTo);
        EIMoveObject ev = new EIMoveObject(Cfg.user(), Core.imce(),
                new Path(pathFrom), to.removeLast(), to.last());
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> shutdown() throws Exception
    {
        Util.l(this).warn("shutdown requested");
        ExitCode.SHUTDOWN_REQUESTED.exit();

        return createVoidReply();
    }

    @Override
    public ListenableFuture<TransportPingReply> transportPing(ByteString deviceId, Integer seqPrev,
            Integer seqNext, Boolean forceNext, Boolean ignoreOffline)
            throws Exception
    {
        EITransportPing ev = new EITransportPing(new DID(deviceId),
                seqPrev, seqNext, forceNext, ignoreOffline);
        ev.execute(PRIO);

        TransportPingReply.Builder bd = TransportPingReply.newBuilder();
        if (ev.rtt() != null) bd.setRtt(ev.rtt());
        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<Void> transportFlood(ByteString deviceId, Boolean send,
            Integer seqStart, Integer seqEnd, Long duration, @Nullable String sname)
            throws Exception
    {
        EITransportFlood ev = new EITransportFlood(new DID(deviceId),
                send, seqStart, seqEnd, duration, sname);
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<TransportFloodQueryReply> transportFloodQuery(ByteString deviceId,
            Integer seq)
            throws Exception
    {
        EITransportFloodQuery ev = new EITransportFloodQuery(new DID(deviceId), seq);
        ev.execute(PRIO);
        return createReply(TransportFloodQueryReply.newBuilder()
                .setBytes(ev.bytes_()).setTime(ev.time_()).build());
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
        EIGetACL ev = new EIGetACL(UserID.fromExternal(user), new Path(path), Core.imce());
        ev.execute(PRIO);

        // we only get here if the event suceeded properly

        GetACLReply.Builder replyBuilder = GetACLReply.newBuilder();
        for (Entry<UserID, Role> en : ev._subject2role.entrySet()) {
            replyBuilder.addSubjectRole(PBSubjectRolePair.newBuilder()
                    .setSubject(en.getKey().toString())
                    .setRole(en.getValue().toPB()));
        }

        return createReply(replyBuilder.build());
    }

    @Override
    public ListenableFuture<Void> updateACL(String user, PBPath path, List<PBSubjectRolePair> srps)
            throws Exception
    {
        // TODO: accepting {@code user} as input is sort of OK as long as the GUI is the only
        // Ritual client but it will become a major security issue if/when Ritual is open-sourced
        Map<UserID, Role> map = Maps.newTreeMap();
        for (PBSubjectRolePair srp : srps) {
            map.put(UserID.fromExternal(srp.getSubject()), Role.fromPB(srp.getRole()));
        }

        EIUpdateACL ev = new EIUpdateACL(UserID.fromExternal(user), new Path(path), map, Core.imce());
        ev.execute(PRIO);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> deleteACL(String user, PBPath path, List<String> subjects)
            throws Exception
    {
        // TODO: accepting {@code user} as input is sort of OK as long as the GUI is the only
        // Ritual client but it will become a major security issue if/when Ritual is open-sourced
        EIDeleteACL ev = new EIDeleteACL(UserID.fromExternal(user), new Path(path),
                UserID.fromExternal(subjects), Core.imce());
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
    public ListenableFuture<ListConflictsReply> listConflicts() throws Exception
    {
        EIListConflicts ev = new EIListConflicts(Core.imce());
        ev.execute(PRIO);
        return createReply(ListConflictsReply.newBuilder()
                .addAllConflict(ev._pathList).build());
    }

    @Override
    public ListenableFuture<ExportConflictReply> exportConflict(PBPath path, Integer kindex)
            throws Exception
    {
        EIExportConflict ev = new EIExportConflict(Core.imce(), new Path(path), new KIndex(kindex));
        ev.execute(PRIO);
        return createReply(ExportConflictReply.newBuilder()
                .setDest(ev._dst.getAbsolutePath()).build());
    }

    @Override
    public ListenableFuture<Void> deleteConflict(PBPath path, Integer kindex) throws Exception
    {
        EIDeleteBranch ev = new EIDeleteBranch(Cfg.user(), Core.imce(),
                new Path(path), new KIndex(kindex));
        ev.execute(PRIO);
        return createVoidReply();
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
        for (PBSyncStatus dss : ev._peers) bd.addStatus(dss);
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
        new EIReloadConfig().execute(PRIO);
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

    @Override
    public ListenableFuture<Void> testLogSendDefect() throws Exception
    {
        SVClient.logSendDefectSync(false, "testing sv defect reporting", null, null, false);
        return createVoidReply();
    }

    @Override
    public ListenableFuture<Void> testMultiuserJoinRootStore(String user)
            throws Exception
    {
        new EITestMultiuserJoinRootStore(UserID.fromExternal(user), Core.imce()).execute(PRIO);
        return createVoidReply();
    }
}
