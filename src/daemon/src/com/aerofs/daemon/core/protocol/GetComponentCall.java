package com.aerofs.daemon.core.protocol;

import java.sql.SQLException;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetComCall;
import com.aerofs.proto.Core.PBGetComCall.Builder;

// TODO NAK for this and other primitives

// we split the code for this protocol primitive into classes due to complexity

public class GetComponentCall
{
    private static final Logger l = Util.l(GetComponentCall.class);

    private IEmigrantTargetSIDLister _emc;
    private PrefixVersionControl _pvc;
    private NativeVersionControl _nvc;
    private MapAlias2Target _a2t;
    private RPC _rpc;
    private DirectoryService _ds;
    private IPhysicalStorage _ps;
    private LocalACL _lacl;
    private NSL _nsl;
    private GCCSendContent _sendContent;

    @Inject
    public void inject_(NSL nsl, LocalACL lacl, IPhysicalStorage ps,
            DirectoryService ds, RPC rpc, PrefixVersionControl pvc, NativeVersionControl nvc,
            IEmigrantTargetSIDLister emc, GCCSendContent sendContent, MapAlias2Target a2t)
    {
        _nsl = nsl;
        _lacl = lacl;
        _ps = ps;
        _ds = ds;
        _rpc = rpc;
        _pvc = pvc;
        _nvc = nvc;
        _emc = emc;
        _sendContent = sendContent;
        _a2t = a2t;
    }

    /**
     * @return the response Message received from the remote peer
     */
    public DigestedMessage remoteRequestComponent_(SOCID socid, To src, Token tk)
            throws Exception
    {
        abortIfComponentIsAliasedContent(socid);

        // Several of the version control and physical storage classes require a branch, not socid.
        // We know that downloads will only ever act on the master branch.
        SOCKID sockid = new SOCKID(socid, KIndex.MASTER);
        if (l.isDebugEnabled()) l.debug("req gcc for " + socid);

        Version vLocal = _nvc.getLocalVersion_(sockid);

        PBGetComCall.Builder bd = PBGetComCall.newBuilder()
            .setObjectId(socid.oid().toPB())
            .setComId(socid.cid().getInt())
            .setLocalVersion(vLocal.toPB_());
        // TODO (DF): Look into how the receiver uses the localVersion. Should we send all
        // versions?  Does the receiver care for which branch we're sending versions?

        if (socid.cid().equals(CID.CONTENT)) setIncrementalDownloadInfo_(socid, bd);

        PBCore call = CoreUtil.newCall(Type.GET_COM_CALL)
            .setGetComCall(bd).build();

        return _rpc.do_(src, socid.sidx(), call, tk, "gcc " + socid + " " + src);
    }


    /**
     * @throws ExAborted if socid is content for an aliased object
     * N.B. also asserts on version invariants for an aliased socid
     */
    private void abortIfComponentIsAliasedContent(SOCID socid)
            throws SQLException, ExAborted
    {
        if (!_a2t.isAliased_(socid.soid())) return;

        // socid is a locally-aliased object

        // all of its local versions must be alias ticks
        final Version vAllLocal = _nvc.getAllLocalVersions_(socid);
        assert vAllLocal.withoutAliasTicks_().isZero_() : socid + " " + vAllLocal;

        final Version vKML = _nvc.getKMLVersion_(socid);
        if (socid.cid().isMeta()) {
            // Aliased meta should only have alias ticks
            assert vKML.withoutAliasTicks_().isZero_() : socid + " " + vKML;
        } else {
            assert (socid.cid().equals(CID.CONTENT)) : socid;

            // Aliased content should have *no* ticks.
            // Furthermore, if we get here it's because we *just* aliased the meta-data and
            // didn't abort the Download loop. Should abort the request, since any content
            // should be downloaded for the target object
            assert vKML.isZero_() : socid + " " + vKML;
            throw new ExAborted("empty KML for aliased " + socid);
        }
    }

    private void setIncrementalDownloadInfo_(SOCID socid, Builder bd)
            throws SQLException, ExNotFound
    {
        OA oa = _ds.getOANullable_(socid.soid());
        if (oa == null) return;
        assert oa.isFile();

        // TODO (DF): is this a reasonable usage of IPhysicalStorage?
        // I can't tell if prefix files should even track branches
        SOCKID branch = new SOCKID(socid, KIndex.MASTER);
        IPhysicalPrefix prefix = _ps.newPrefix_(branch);
        long len = prefix.getLength_();
        if (len == 0) return;

        Version vPre = _pvc.getPrefixVersion_(branch.soid(), branch.kidx());
        l.debug("prefix ver " + vPre + " len " + len);

        bd.setPrefixLength(len);
        bd.setPrefixVersion(vPre.toPB_());
    }

    public void processCall_(DigestedMessage msg)
        throws Exception
    {
        Util.checkPB(msg.pb().hasGetComCall(), PBGetComCall.class);
        PBGetComCall pb = msg.pb().getGetComCall();

        SOCKID k = new SOCKID(msg.sidx(), new OID(pb.getObjectId()), new CID(pb.getComId()));
        l.info("gcc for " + k + " from " + msg.ep());

        // Give up if the requested SOCKID is not present locally (either meta or content)
        // N.B. An aliased object is reported not present, but we should not throw if the
        // caller requested meta for a locally-aliased object (hence the second AND block)
        // TODO (MJ) does this mean we should change the definition of isPresent to handle
        // aliased objects?
        if (!_ds.isPresent_(k) &&
            !(k.cid().isMeta() && _ds.hasAliasedOA_(k.soid()))) {
            l.debug(k + " not present. Throwing");
            throw new ExNoComponentWithSpecifiedVersion();
        }

        // check permissions
        if (!_lacl.check_(msg.user(), k.sidx(), Role.VIEWER)) {
            l.debug("receiver has no permission");
            throw new ExNoPerm();
        }

        Version vRemote = new Version(pb.getLocalVersion());
        Version vLocal = _nvc.getLocalVersion_(k);

        if (!vLocal.sub_(vRemote).isZero_()) {
            sendReply_(msg, k);
        } else {
            if (l.isDebugEnabled()) {
                l.debug("r " + vRemote + " >= l " + vLocal + ". Throw no_new_update");
            }
            throw new ExNoComponentWithSpecifiedVersion();
        }

        // the kml_version field is used only as a hint for the receiver to
        // know more available versions
        // TODO:
        // Version vRemoteKnown = Version.fromPB(pb.getKmlVersion()).
        // add_(vRemote);
        // Version vKnown = _cd.getKnownVersion_(k.socid());
        //
        // if (!vRemoteKnown.sub_(vKnown).isZero_()) {
        //     l.debug("TODO add diff to db and issue a download");
        // }
    }

    public void sendReply_(DigestedMessage msg, SOCKID k)
        throws Exception
    {
        l.debug("send to " + msg.ep() + " for " + k);

        Version vLocal = _nvc.getLocalVersion_(k);

        PBCore.Builder bdCore = CoreUtil.newReply(msg.pb());

        PBGetComReply.Builder bdReply = PBGetComReply.newBuilder()
            .setVersion(vLocal.toPB_());

        if (k.cid().isMeta()) {
            sendMeta_(msg.did(), k, bdCore, bdReply);
        } else if (k.cid().equals(CID.CONTENT)) {
            _sendContent.send_(msg.ep(), k, bdCore, bdReply, vLocal,
                    msg.pb().getGetComCall().getPrefixLength(),
                    new Version(msg.pb().getGetComCall().getPrefixVersion()));
        } else {
            SystemUtil.fatal("unsupported CID: " + k.cid());
        }
    }

    /* TODO
     *  - don't send ACL to non-member devices
     *  - skip local permission checking on these devices
     *  - build ancestors for leaf nodes on these devices
     */
    private void sendMeta_(DID did, SOCKID k, PBCore.Builder bdCore, PBGetComReply.Builder bdReply)
        throws Exception
    {
        // guaranteed by the caller
        assert _ds.isPresent_(k) || _ds.hasAliasedOA_(k.soid());

        OA oa = _ds.getAliasedOANullable_(k.soid());
        assert oa != null : k;

        // verify that the name we send is in Normalized Form C
        FileUtil.logIfNotNFC(oa.name(), oa.toString());

        PBMeta.Builder bdMeta = PBMeta.newBuilder()
            .setType(toPB(oa.type()))
            .setParentObjectId(oa.parent().toPB())
            .setName(oa.name())
            .setFlags(oa.flags() & ~OA.FLAGS_LOCAL);

        ////////
        // send alias information

        OID targetOID = _a2t.getNullable_(k.soid());
        if (targetOID != null) {
            assert !k.oid().equals(targetOID); // k is alias here.
            bdMeta.setTargetOid(targetOID.toPB());
            Version vTarget = _nvc.getLocalVersion_(new SOCKID(k.sidx(), targetOID, CID.META));
            bdMeta.setTargetVersion(vTarget.toPB_());
            l.debug("Sending target oid: " + targetOID + " target version: "
                   + vTarget + " alias SOCKID: " + k);
        }

        ////////
        // send emigration info

        for (SID sid : _emc.getEmigrantTargetAncestorSIDsForMeta_(oa.parent(), oa.name())) {
            bdMeta.addEmigrantTargetAncestorSid(sid.toPB());
        }

        bdReply.setMeta(bdMeta);

        _nsl.sendUnicast_(did, k.sidx(), bdCore.setGetComReply(bdReply).build());
    }

    private static PBMeta.Type toPB(OA.Type type)
    {
        return PBMeta.Type.valueOf(type.ordinal());
    }
}
