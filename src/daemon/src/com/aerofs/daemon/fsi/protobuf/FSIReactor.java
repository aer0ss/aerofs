package com.aerofs.daemon.fsi.protobuf;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.admin.*;
import com.aerofs.daemon.event.admin.EIListConflicts.ConflictEntry;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.fsi.FSIFile;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.Path;
import com.aerofs.proto.Fsi.*;
import com.aerofs.proto.Fsi.PBFSIReply.Builder;
import com.aerofs.proto.Fsi.PBListConflictsReply.PBConflictEditors;
import org.apache.log4j.Logger;

import java.security.PrivateKey;
import java.util.Map.Entry;

public class FSIReactor implements IReactor
{
    private static final Logger l = Util.l(FSIReactor.class);

    @Override
    public void reactorDisconnected_()
    {
    }

    @Override
    public byte[][] getReactorPreamble_()
    {
        return null;
    }

    public static PBObjectAttr toPB(OA oa)
    {
        PBObjectAttr.Builder builder = PBObjectAttr.newBuilder()
            .setObjectId(oa.soid().oid().toPB())
            .setName(oa.name())
            .setIsDir(!oa.isFile())
            .setIsAnchor(oa.isAnchor())
            .setFlags(oa.flags());

        // TODO return contributing device ids for each non-master branch and
        // suffix their file names with the name of these devices. cf. #82.

        if (oa.isFile()) {
            for (Entry<KIndex, CA> en : oa.cas().entrySet()) {
                builder.addContentAttr(PBContentAttr.newBuilder()
                        .setKidx(en.getKey().getInt())
                        .setLength(en.getValue().length()));
            }
        }

        return builder.build();
    }

    @Override
    public byte[][] react_(byte[] bs, int wirelen) throws Exception
    {
        PBFSIReply.Builder reply = PBFSIReply.newBuilder();
        try {
            PBFSICall call = PBFSICall.parseFrom(bs);

            String user;
            if (call.hasUser()) {
                user = call.getUser();
                l.info(call.getType() + " by " + user);
            } else {
                user = Cfg.user();
                l.info(call.getType());
            }

            switch (call.getType()) {
            case LIST_CHILDREN:
                listChildren(user, call, reply);
                break;
            case MOVE_OBJECT:
                moveObject(user, call);
                break;
            case GET_ATTR:
                getAttr(user, call, reply);
                break;
            case MKDIR:
                mkdir(user, call);
                break;
            case DELETE_OBJECT:
                deleteObject(user, call);
                break;
            case DELETE_BRANCH:
                deleteBranch(user, call);
                break;
            case SET_ATTR:
                setAttr(user, call);
                break;
            case SET_PRIVATE_KEY:
                setKey(call);
                break;
            case TRANSPORT_PING:
                transportPing(call, reply);
                break;
            case TRANSPORT_FLOOD:
                transportFlood(call);
                break;
            case TRANSPORT_FLOOD_QUERY:
                transportFloodQuery(call, reply);
                break;
            case LIST_CONFLICTS:
                listConflicts(reply);
                break;
            case SAVE_REVISION:
                saveRevision(call);
                break;
            default:
                throw new ExProtocolError(PBFSICall.Type.class);
            }

        } catch (Exception e) {
            PBFSIReply r = PBFSIReply.newBuilder()
                .setException(Exceptions.toPBWithStackTrace(e))
                .build();
            return new byte[][] { r.toByteArray() };
        }

        PBFSIReply ack = reply.build();
        return new byte[][] { ack.toByteArray() };
    }

    private void transportFloodQuery(PBFSICall call, Builder reply) throws Exception
    {
        Util.checkPB(call.hasTransportFloodQuery(), PBTransportFloodQueryCall.class);

        PBTransportFloodQueryCall pb = call.getTransportFloodQuery();
        EITransportFloodQuery ev = new EITransportFloodQuery(new DID(pb.getDeviceId()),
                pb.getSeq());
        ev.execute(FSIFile.PRIO);
        reply.setTransportFloodQuery(PBTransportFloodQueryReply.newBuilder()
                .setBytes(ev.bytes_())
                .setTime(ev.time_()));
    }

    private void transportFlood(PBFSICall call) throws Exception
    {
        Util.checkPB(call.hasTransportFlood(), PBTransportFloodCall.class);

        PBTransportFloodCall pb = call.getTransportFlood();
        EITransportFlood ev = new EITransportFlood(new DID(pb.getDeviceId()),
                pb.getSend(), pb.getSeqStart(), pb.getSeqEnd(), pb.getDuration(),
                pb.hasSname() ? pb.getSname() : null);
        ev.execute(FSIFile.PRIO);
    }

    private void transportPing(PBFSICall call, Builder reply) throws Exception
    {
        Util.checkPB(call.hasTransportPing(), PBTransportPingCall.class);

        PBTransportPingCall pb = call.getTransportPing();

        EITransportPing ev = new EITransportPing(new DID(pb.getDeviceId()),
                pb.getSeqPrev(), pb.getSeqNext(), pb.getForceNext(),
                pb.getIgnoreOffline());
        ev.execute(FSIFile.PRIO);

        PBTransportPingReply.Builder bd = PBTransportPingReply.newBuilder();
        if (ev.rtt() != null) bd.setRtt(ev.rtt());
        reply.setTransportPing(bd);
    }

    private void saveRevision(PBFSICall call) throws Exception
    {
        Util.checkPB(call.hasSaveRevision(), PBSaveRevisionCall.class);

        PBSaveRevisionCall pb = call.getSaveRevision();

        Path path = new Path(pb.getPath());
        DID did = new DID(pb.getDeviceId());
        byte[] index = pb.getIndex().toByteArray();
        new EISaveRevision(path, did, index, pb.getDestination())
            .execute(FSIFile.PRIO);
    }

    private void listConflicts(Builder reply) throws Exception
    {
        EIListConflicts ev = new EIListConflicts();
        ev.execute(FSIFile.PRIO);

        PBListConflictsReply.Builder bd = PBListConflictsReply.newBuilder();
        for (ConflictEntry ce : ev._conflicts) {
            bd.addPath(ce._path.toPB());
            bd.addKidx(ce._kidx.getInt());
            bd.addFsPath(ce._fspath);
            bd.addEditors(PBConflictEditors.newBuilder()
                    .addAllEditor(ce._editors));
        }
        reply.setListConflicts(bd);
    }

    private void setKey(PBFSICall call) throws Exception
    {
        Util.checkPB(call.hasSetPrivateKey(), PBSetPrivateKeyCall.class);

        PrivateKey key = SecUtil.decodePrivateKey(
                call.getSetPrivateKey().getKey().toByteArray());
        new EISetPrivateKey(key).execute(FSIFile.PRIO);
    }

    private void setAttr(String user, PBFSICall call)
            throws Exception
    {
        Util.checkPB(call.hasSetAttr(), PBSetAttrCall.class);

        PBSetAttrCall setAttr = call.getSetAttr();
        Path path = new Path(setAttr.getPath());

        new FSIFile(user, path, Core.imce()).setAttr(setAttr.hasFlags() ? setAttr.getFlags() : null);
    }

    private void moveObject(String user, PBFSICall call)
            throws Exception
    {
        Util.checkPB(call.hasMoveObject(), PBMoveObjectCall.class);
        Path from = new Path(call.getMoveObject().getFrom());
        Path toParent = new Path(call.getMoveObject().getToParent());

        new FSIFile(user, from).move(toParent, call.getMoveObject().getToName());
    }

    private void listChildren(String user, PBFSICall call, Builder reply)
        throws Exception
    {
        Util.checkPB(call.hasListChildrenAttr(), PBListChildrenAttrCall.class);
        Path path = new Path(call.getListChildrenAttr().getPath());

        EIGetChildrenAttr ev = new EIGetChildrenAttr(user, path, Core.imce());
        ev.execute(FSIFile.PRIO);

        PBListChildrenAttrReply.Builder bdLCA = PBListChildrenAttrReply.newBuilder();
        for (OA oa : ev._oas) {
            bdLCA.addChildAttr(toPB(oa));
        }

        reply.setListChildrenAttr(bdLCA);
    }

    // TODO remove when fully migrated to ritual.proto
    private void getAttr(String user, PBFSICall call, Builder reply)
            throws Exception
    {
        Util.checkPB(call.hasGetAttr(), PBGetAttrCall.class);

        PBGetAttrCall ga = call.getGetAttr();
        Path path = new Path(ga.getPath());

        OA oa = new FSIFile(user, path).getAttrThrows();

        PBGetAttrReply.Builder bdGAReply = PBGetAttrReply.newBuilder()
                .setAttr(toPB(oa));
        reply.setGetAttr(bdGAReply);
    }

    private void mkdir(String user, PBFSICall call)
            throws Exception
    {
        Util.checkPB(call.hasMkdir(), PBMkdirCall.class);
        new FSIFile(user, new Path(call.getMkdir().getPath()))
            .mkdir(call.getMkdir().getExclusive());
    }

    private void deleteObject(String user, PBFSICall call)
            throws Exception
    {
        Util.checkPB(call.hasDeleteObject(), PBDeleteObjectCall.class);
        new FSIFile(user, new Path(call.getDeleteObject().getPath()))
            .delete();
    }

    private void deleteBranch(String user, PBFSICall call)
            throws Exception
    {
        Util.checkPB(call.hasDeleteBranch(), PBDeleteBranchCall.class);
        new FSIFile(user, new Path(call.getDeleteBranch().getPath()))
            .deleteBranch(new KIndex(call.getDeleteBranch().getKidx()));
    }
}
