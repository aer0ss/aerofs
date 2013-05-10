package com.aerofs.daemon.core.alias;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.download.IDownloadContext;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.inject.Inject;


import com.aerofs.daemon.core.protocol.ReceiveAndApplyUpdate;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.*;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.daemon.core.protocol.ReceiveAndApplyUpdate.*;

/**
 * Implements aliasing of objects to resolve name conflict.
 *
 * For description of the problem, motivation, special cases etc. please refer to the detailed
 * documentation located at path-to-repo/docs/design/name_conflicts/name_conflict_resolution.doc
 *
 * Terms useful to understand code and comments:
 *  alias object: Object on which aliasing operation is being performed.
 *  target object: Object to which alias object will be mapped.
 *  aliased object: Object on which aliasing operation has been performed locally.
 *  alias message: Message received in response to GetComponentCall which indicates that object for which
 *          GetComponentCall was made has been aliased on the remote device. The message includes
 *          the optional target oid and meta-data version of the target object.
 *  non-alias message: Message received in response to GetComponentCall which indicates that object for
 *          which GetComponentCall was made is not aliased on the remote device. The message is
 *          used to receive meta-data or content update from the object on the remote device.
 */
public class Aliasing
{
    private static final Logger l = Loggers.getLogger(Aliasing.class);

    private DirectoryService _ds;
    private NativeVersionControl _nvc;
    private VersionUpdater _vu;
    private ReceiveAndApplyUpdate _ru;
    private AliasingMover _almv;
    private MapAlias2Target _a2t;
    private TransManager _tm;

    @Inject
    public void inject_(DirectoryService ds, NativeVersionControl nvc, TransManager tm,
            VersionUpdater vu, ReceiveAndApplyUpdate ru, AliasingMover almv, MapAlias2Target a2t)
    {
        _ds = ds;
        _nvc = nvc;
        _vu = vu;
        _ru = ru;
        _almv = almv;
        _a2t = a2t;
        _tm = tm;
    }

    public static final class AliasAndTarget
    {
        final SOID _alias;
        final SOID _target;

        AliasAndTarget(SOID alias, SOID target)
        {
            _alias = alias;
            _target = target;
        }
    }

    /**
     * Determines alias and target among the given soids.
     *
     * @return AliasAndTarget contains the selected alias and target.
     */
    public static AliasAndTarget determineAliasAndTarget_(SOID soid1, SOID soid2)
    {
        int comp = soid1.compareTo(soid2);
        assert comp != 0;
        if (comp > 0) {
            // soid1 wins.
            return new AliasAndTarget(soid2, soid1);
        } else {
            // soid2 wins.
            return new AliasAndTarget(soid1, soid2);
        }
    }

    /**
     * The core method of aliasing algorithm that performs all the necessary state modifications
     * to merge the alias object into target.
     *
     * This method assumes that meta-data information of the target object is present locally.
     *
     * Caller is responsible for assigning new version to the alias object.
     *
     * On completion of this method only alias to target mapping information will be retained about
     * the alias object.
     */
    private void performAliasing_(SOID alias, Version vAliasMeta, SOID target, Version vTargetMeta,
            Trans t) throws Exception
    {
        l.debug("Aliasing soids, alias:" + alias + " target: " + target);

        assert !alias.equals(target);
        assert alias.sidx().equals(target.sidx());

        SOCID aliasMeta = new SOCID(alias, CID.META);
        SOCID aliasContent = new SOCID(alias, CID.CONTENT);
        SOCID targetMeta = new SOCID(target, CID.META);
        SOCID targetContent = new SOCID(target, CID.CONTENT);

        dumpVersions_(targetMeta, targetContent, aliasMeta, aliasContent);

        // Only non-aliased ticks from meta-data component of alias object should be moved
        // to target.
        // TODO:FIXME this may lose ticks...
        vAliasMeta = vAliasMeta.withoutAliasTicks_();

        // KML version should be updated before merging local version to avoid assertion failures in
        // VersionControl.java.
        _almv.moveKMLVersion_(aliasMeta, targetMeta, vAliasMeta, vTargetMeta, t);

        // Move the meta-data versions.
        _almv.moveMetadataLocalVersion_(aliasMeta, targetMeta, vAliasMeta, vTargetMeta, t);

        _almv.moveContentOrChildren_(alias, target, t);

        _a2t.add_(alias, target, t);

        // With the KML version of the alias object being moved to target object, if there
        // is a CollectorSeq for the alias object then it'll be deleted when the Collector
        // iterates over CollectorSeq numbers since the KML version of alias object
        // will be zero.
        assert _nvc.getKMLVersion_(aliasMeta).withoutAliasTicks_().isZero_();


        dumpVersions_(targetMeta, targetContent, aliasMeta, aliasContent);
    }

    /**
     * This method assumes that meta-data information of both alias and target objects are present
     * locally.
     */
    private void performAliasingOnLocallyAvailableObjects_(SOID alias, Version vAliasMeta,
            SOID target, Version vTargetMeta, Trans t) throws Exception
    {
        assert _ds.hasOA_(alias);
        assert _ds.hasOA_(target);

        performAliasing_(alias, vAliasMeta, target, vTargetMeta, t);
    }

    /**
     * Processes a non-alias message on a locally aliased object.
     *
     * KML version of the alias is moved to the target and the message is dropped.
     *
     * @param alias SOCID of the alias
     * @param targetOIDLocal OID of the local target to which the alias object points to.
     */
    public void processNonAliasMsgOnLocallyAliasedObject_(SOCID alias, OID targetOIDLocal)
        throws SQLException
    {
        // Non-alias message is received for a locally aliased object.
        // No action is required in this case because anti-entropy will ensure that remote updates
        // on alias are sent via target oid later when sender learns about alias-->target mapping.
        // Drop the message and move non-alias part of KML version of alias to target.

        l.debug("This peer has performed aliasing for alias: " + alias + " to target: " +
            targetOIDLocal + ". Dropping message.");

        Trans t = _tm.begin_();
        try {
            SOCID target = new SOCID(alias.sidx(), targetOIDLocal, CID.META);

            // TODO:FIXME this may lose ticks...
            Version vKMLAlias = _nvc.getKMLVersion_(alias).withoutAliasTicks_();

            _nvc.deleteKMLVersionPermanently_(alias, vKMLAlias, t);
            Version vAllTarget = _nvc.getAllVersions_(target);
            Version vKMLAdd = vKMLAlias.sub_(vAllTarget);
            _nvc.addKMLVersionAndCollectorSequence_(target, vKMLAdd, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Processes the alias message received from remote peer in response to GetComponentCall.
     * If the target object is not known locally then it'll be processed using
     * ReceiveAndApplyUpdate class.
     *
     * Please refer to inline comments for details of processing.
     *
     * @param vRemoteAliasMeta Meta-data version of the alias from the remote peer
     * @param vRemoteTargetMeta Meta-data version of the target from the remote peer
     * @param metaDiff Meta-diff between local object and remote alias object
     * @param meta Meta-data information sent by the remote peer about alias
     */
    public void processAliasMsg_(SOID alias, Version vRemoteAliasMeta, SOID target,
            Version vRemoteTargetMeta, OID oidParent, int metaDiff, PBMeta meta,
            IDownloadContext cxt)
            throws Exception
    {
        // Alias message processing is only for meta-data updates.
        assert meta.hasTargetVersion();
        SIndex sidx = alias.sidx();
        assert sidx.equals(target.sidx());

        l.info("alias msg, alias: {} vAlias: {} target: {} vTarget: {} p: {}",
               alias, vRemoteAliasMeta, target, vRemoteTargetMeta, oidParent);

        Trans t = _tm.begin_();
        Throwable rollbackCause = null;
        try {
            if (!_ds.hasAliasedOA_(target)) {
                l.info("target not locally present");
                SOCKID k = new SOCKID(target, CID.META, KIndex.MASTER);

                // Although CausalityResult is only used in applyUpdateMetaAndContent_()
                // when no name conflict is detected it's necessary to compute it before
                // applyMeta_().
                CausalityResult cr = _ru.computeCausalityForMeta_(target, vRemoteTargetMeta,
                        metaDiff);

                boolean oidsAliasedOnNameConflict = _ru.applyMeta_(target, meta, oidParent,
                        false, // Since this is a new object to be received, wasPresent is false.
                        metaDiff, t,
                        alias, // noNewVersion
                        vRemoteTargetMeta,
                        alias,
                        cr,
                        cxt);

                // Don't applyUpdate() if a name conflict was detected and
                // performAliasingOnLocallyAvailableObjects_() was invoked.
                if (!oidsAliasedOnNameConflict) {
                    _ru.applyUpdateMetaAndContent_(k, vRemoteTargetMeta, cr, t);
                }
                l.debug("Done receiving new target object");
            }
            assert _ds.hasAliasedOA_(target);

            // Check whether target is aliased locally, and update target if so.
            target = _a2t.dereferenceAliasedOID_(target);

            // Ensure target is not aliased locally.
            assert _ds.hasOA_(target);
            assert !_a2t.isAliased_(target);
            SOCID aliasMeta = new SOCID(alias, CID.META);
            if (_ds.hasAliasedOA_(alias)) {
                OID targetLocalOID = _a2t.getNullable_(alias);
                if (targetLocalOID == null) {
                    l.info("alias and target present locally");
                    Version vAliasMeta = _nvc.getLocalVersion_(new SOCKID(alias, CID.META));
                    Version vTargetMeta = _nvc.getLocalVersion_(new SOCKID(target, CID.META));
                    performAliasingOnLocallyAvailableObjects_(alias, vAliasMeta, target,
                        vTargetMeta, t);
                } else if (!targetLocalOID.equals(target.oid())) {
                    l.info("name conflict between multiple targets {} {}", alias, targetLocalOID);
                    AliasAndTarget ar = determineAliasAndTarget_(new SOID(sidx, targetLocalOID),
                        target);
                    Version vAliasMeta = _nvc.getLocalVersion_(new SOCKID(ar._alias, CID.META));
                    Version vTargetMeta = _nvc.getLocalVersion_(new SOCKID(ar._target, CID.META));
                    performAliasingOnLocallyAvailableObjects_(ar._alias, vAliasMeta, ar._target,
                        vTargetMeta, t);
                } else {
                    // Aliasing operation already performed on this device for alias object.
                    l.debug("Object locally aliased to target");
                }

            } else {
                l.info("add alias entry");
                // New version during aliasing operation is generated only for meta-data
                // and not for content of alias object. Hence KML version for content can be
                // safely moved from alias to target.

                // earlier code asserts _ds.hasOA_(target)
                OA oaTarget = _ds.getOA_(target);

                // Move KML version of the alias content to the target, if it's a file. Non-files
                // don't have contents.
                if (oaTarget.isFile()) {
                    // TODO: Consider moving following processing to a helper method or
                    // reuse an existing method.
                    SOCID aliasContent = new SOCID(alias, CID.CONTENT);
                    SOCID targetContent = new SOCID(target, CID.CONTENT);

                    Version vKMLAliasContent = _nvc.getKMLVersion_(aliasContent);
                    Version vAllTargetContent = _nvc.getAllVersions_(targetContent);
                    Version vKMLAdd = vKMLAliasContent.sub_(vAllTargetContent);
                    _nvc.addKMLVersionAndCollectorSequence_(targetContent, vKMLAdd, t);
                    _nvc.deleteKMLVersionPermanently_(aliasContent, vKMLAliasContent, t);
                }

                // Move non-alias meta KML version from alias to target.
                // TODO:FIXME this may lose ticks...
                Version vKMLAliasMeta = _nvc.getKMLVersion_(aliasMeta).withoutAliasTicks_();
                _nvc.deleteKMLVersionPermanently_(aliasMeta, vKMLAliasMeta, t);

                SOCID targetMeta = new SOCID(target, CID.META);
                Version vAllTargetMeta = _nvc.getAllVersions_(targetMeta);
                Version vKMLAdd = vKMLAliasMeta.sub_(vAllTargetMeta);
                _nvc.addKMLVersionAndCollectorSequence_(targetMeta, vKMLAdd, t);

                _a2t.add_(alias, target, t);
            }

            Version vLocalAlias = getMasterVersion_(aliasMeta);
            Version vAddLocal = vRemoteAliasMeta.sub_(vLocalAlias);

            // Update KML version before adding to local version.
            Version vKML = _nvc.getKMLVersion_(aliasMeta);
            Version vKML_addLocal = vKML.sub_(vAddLocal);
            Version vDelKML = vKML.sub_(vKML_addLocal);

            _nvc.deleteKMLVersion_(aliasMeta, vDelKML, t);
            _nvc.addLocalVersion_(new SOCKID(aliasMeta), vAddLocal, t);

            t.commit_();
            l.debug("Done processing alias message");

        // See {@link com.aerofs.daemon.lib.db.trans.Trans#end_()} for the reason of these blocks
        } catch (Exception e) {
            rollbackCause = e;
            l.warn("triggered rollback: " + Util.e(rollbackCause));
            throw e;
        } catch (Error e) {
            rollbackCause = e;
            l.warn("triggered rollback: " + Util.e(rollbackCause));
            throw e;
        } finally {
            t.end_(rollbackCause);
        }
    }

    /**
     * Resolves name conflict by performing aliasing operation.
     * This method should be used when a name conflict is detected on processing a new object
     * from a remote peer.
     *
     * @param meta Meta-data information received from remote peer
     * @param soidNoNewVersion No new version should be created if the resulting
     *        alias matches soidNoNewVersion
     */
    public void resolveNameConflictOnNewRemoteObjectByAliasing_(SOID soidRemote, SOID soidLocal,
            OID parent, Version vRemote, PBMeta meta, @Nullable SOID soidNoNewVersion, Trans t)
        throws Exception
    {
        l.debug("Resolving name conflict by aliasing conflicting objects.");
        assert soidRemote.sidx().equals(soidLocal.sidx()) : soidRemote + " " + soidLocal;

        Version vLocal = getMasterVersion_(new SOCID(soidLocal, CID.META));

        AliasAndTarget ar = determineAliasAndTarget_(soidLocal, soidRemote);

        boolean targetIsRemote = ar._target.equals(soidRemote);

        l.info("alias:" + ar._alias + " target:" + ar._target + " " + targetIsRemote);

        Version vAlias, vTarget;
        if (targetIsRemote) {
            vAlias = vLocal;
            vTarget = vRemote;
        } else {
            vAlias = vRemote;
            vTarget = vLocal;
        }

        performAliasing_(ar._alias, vAlias, ar._target, vTarget, t);

        OA oaTarget = _ds.getOA_(ar._target);
        l.info("target: " + oaTarget);

        // Increment local version of the alias object, if required.
        if (!ar._alias.equals(soidNoNewVersion)) {
            l.info("update alias " + ar._alias);
            _vu.updateAliased_(new SOCKID(ar._alias, CID.META), t);
        }
    }

    /**
     * Helper method to dump version vectors. Useful for debugging.
     */
    private void dumpVersions_(SOCID targetMeta, SOCID targetContent, SOCID aliasMeta,
            SOCID aliasContent) throws SQLException
    {
        if (l.isDebugEnabled()) {
            l.debug(" vTargetMeta: " + getMasterVersion_(targetMeta) +
                   " vTargetContent: " + getMasterVersion_(targetContent) +
                   " vAliasMeta: " + getMasterVersion_(aliasMeta) +
                   " vAliasContent: " + getMasterVersion_(aliasContent) +
                   " vKMLTargetMeta: " + _nvc.getKMLVersion_(targetMeta) +
                   " vKMLTargetContent: " + _nvc.getKMLVersion_(targetContent) +
                   " vKMLAliasMeta: " + _nvc.getKMLVersion_(aliasMeta) +
                   " vKMLAliasContent: " + _nvc.getKMLVersion_(aliasContent));
        }
    }

    /**
     * @return the version of the master branch of the specified component
     */
    private Version getMasterVersion_(SOCID socid) throws SQLException
    {
        return _nvc.getLocalVersion_(new SOCKID(socid));
    }

}
