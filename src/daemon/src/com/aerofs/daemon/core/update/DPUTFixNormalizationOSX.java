/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.linked.LinkedPath;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.aerofs.defects.Defects.newMetric;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 * OSX uses a variant of Normal Form D therefore @param{name} can be in NFD.
 * @see{http://developer.apple.com/library/mac/#qa/qa1173/_index.html}
 *
 * However most other platforms use NFC by default (hence Java helpfully normalizing the result of
 * File.list() to NFC)
 *
 * Because OSX is unicode-normalizing (and crucially not normalization-preserving) we cannot use
 * the same "contextual NRO" logic that smoothes case-insensitivity considerations. Instead we need
 * to arbitrarily pick one normal form as the only representable one on OSX.
 *
 * The naive choice originally made was to pick NFD in a foolish attempt to stay as close to the
 * actual filesystem contents. This led to a terrible UX when syncing accented filenames between OSX
 * and non-OSX devices. It also caused serious no-sync issues for users with OSX devices installed
 * before the change due to the absence of a corresponding migration DPUT.
 *
 * This DPUT only does the strict minimum amount of migration. To ensure robustness the most complex
 * changes will actually be handled by the scanner.
 *
 * The only case we need to specifically handle is that of NFC files received from a remote peer
 * after the NRO changes were rolled out. These files will have been marked as NRO but should in
 * fact be visible. We need to manually move them otherwise the physical storage layer will be
 * confused by their absence.
 */
public class DPUTFixNormalizationOSX implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTFixNormalizationOSX.class);

    private final IDBCW _dbcw;
    private final IMetaDatabase _mdb;
    private final IStoreDatabase _sdb;
    private final ISIDDatabase _siddb;
    private final NRODatabase _nrodb;

    private final IOSUtil _osutil;
    private final InjectableDriver _dr;

    private static class Counters
    {
        int nfc;
        int nro;
        int wtf;
        int logicalConflict;
        int physicalConflict;
    }


    public DPUTFixNormalizationOSX(IOSUtil osutil, CoreDBCW dbcw, InjectableDriver dr)
    {
        _osutil = osutil;
        _dbcw = dbcw.get();
        _dr =  dr;
        _mdb = new MetaDatabase(dbcw);
        _sdb = new StoreDatabase(dbcw);
        _siddb = new SIDDatabase(dbcw);
        _nrodb = new NRODatabase(dbcw);
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (_osutil.getOSFamily() == OSFamily.OSX
                    && Cfg.storageType() == StorageType.LINKED) {
                Counters c = new Counters();
                renormalize(s, c);
                newMetric("dput.osx.renormalize")
                        .addData("counters", c)
                        .sendAsync();
            }
        });
    }

    private void renormalize(Statement s, Counters c) throws SQLException
    {

        try (ResultSet rs = s.executeQuery(
                DBUtil.selectWhere(T_OA, C_OA_FLAGS + "=0", C_OA_SIDX, C_OA_OID, C_OA_NAME))) {
            while (rs.next()) {
                SOID soid = new SOID(new SIndex(rs.getInt(1)), new OID(rs.getBytes(2)));
                String name = rs.getString(3);
                if (!soid.oid().isRoot()) {
                    try {
                        renormalize(soid, name, c);
                    } catch (IOException e) {
                        throw new SQLException(e);
                    }
                }
            }
        }
    }

    private static String P(File f)
    {
        return P(f.getPath());
    }

    // hex-encode filenames before printing
    private static String P(String f)
    {
        return BaseUtil.hexEncode(BaseUtil.string2utf(f));
    }

    private void renormalize(SOID soid, String name, Counters c) throws SQLException, IOException
    {
        // only need to fix remotely-received NFC files
        String nfc = Normalizer.normalize(name, Form.NFC);
        String nfd = Normalizer.normalize(name, Form.NFD);
        if (nfc.equals(nfd) || !name.equals(nfc)) return;

        ++c.nfc;
        l.info("fix {} {}", soid, P(name));

        // NFC files should not have entries in the NRO db on OSX
        Preconditions.checkState(!_nrodb.isNonRepresentable_(soid));

        ResolvedPath path = resolve(checkNotNull(_mdb.getOA_(soid)));

        String nroPath = nroPath(path);
        File nro = new File(nroPath);

        // no NRO to move (locally-created file or only META was downloaded)
        // this check also allows incremental progress (see end of method)
        if (!nro.exists()) return;
        ++c.nro;

        String absPath = Util.join(physicalPath(path.parent()), name);
        File phy = new File(absPath);
        SOID phySOID = phyObject(absPath);
        OA oa = phySOID != null ? _mdb.getOA_(phySOID) : null;

        l.info("  {} {}", phySOID, P(absPath));

        // no NRO to move
        if (soid.equals(phySOID)) {
            ++c.wtf;
            l.warn("NRO and phy?");
            return;
        }

        // move physical object out of the way
        // (unlikely to be encountered in the wild but better safe than sorry)
        if (phy.exists()) {
            l.info("obs {} {}", phySOID, oa != null ? P(oa.name()) : "");
            if (oa != null && !Normalizer.isNormalized(oa.name(), Form.NFC)) {
                String nro2 = nroPath(resolve(oa));
                ++c.logicalConflict;
                l.info("mv {} {} {}", soid, P(phy), P(nro2));
                FileUtil.moveInSameFileSystem(phy, new File(nro2));
            } else {
                ResolvedPath pParent = path.parent();
                SOID parent = pParent.isEmpty()
                        ? new SOID(path.soid().sidx(), OID.ROOT)
                        : pParent.soid();
                String phy2 = conflictFreeName(name, parent, phy.getParentFile());
                ++c.physicalConflict;
                l.info("mv {} {} {}", soid, P(phy), P(phy2));
                FileUtil.moveInSameFileSystem(phy, new File(phy.getParentFile(), phy2));
            }
        }

        // move NRO to visible
        // NB: incremental progress If the DPUT fails or is interrupted, successful moves
        // need not be rolled back
        l.info("mv {} {} {}", soid, P(nro), P(phy));
        FileUtil.moveInSameFileSystem(nro, phy);
    }

    private ResolvedPath resolve(@Nonnull OA oa) throws SQLException
    {
        return resolve(_sdb, _siddb, _mdb, oa);
    }

    static ResolvedPath resolve(IStoreDatabase sdb, ISIDDatabase siddb, IMetaDatabase mdb,
            @Nonnull OA oa) throws SQLException
    {
        List<SOID> soids = Lists.newArrayListWithCapacity(16);
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (true) {
            if (oa.soid().oid().isRoot()) {
                Set<SIndex> p = sdb.getParents_(oa.soid().sidx());
                if (L.isMultiuser() || p.isEmpty()) {
                    break;
                } else {
                    checkState(p.size() == 1, oa.soid().sidx() + " " + p);
                    // parent oid of the root encodes the parent store's sid
                    SOID soidAnchor = new SOID(Iterables.get(p, 0),
                            SID.storeSID2anchorOID(siddb.getSID_(oa.soid().sidx())));
                    checkState(!soidAnchor.equals(oa.soid()), soidAnchor + " " + oa);
                    oa = checkNotNull(mdb.getOA_(soidAnchor));
                }
            }

            soids.add(oa.soid());
            elems.add(oa.name());
            checkState(!oa.parent().equals(oa.soid().oid()), oa);
            oa = checkNotNull(mdb.getOA_(new SOID(oa.soid().sidx(), oa.parent())));
        }

        return new ResolvedPath(siddb.getSID_(oa.soid().sidx()),
                Lists.reverse(soids), Lists.reverse(elems));
    }

    private String conflictFreeName(String name, SOID parent, File phyParent)
            throws SQLException
    {
        do {
            name = Util.nextFileName(name);
        } while (new File(phyParent, name).exists()
                || _mdb.getChild_(parent.sidx(), parent.oid(), name) != null);
        return name;
    }

    static String auxRoot(SID sid) throws SQLException
    {
        return Cfg.absAuxRootForPath(Cfg.getRootPathNullable(sid), sid);
    }

    static String auxPath(SID sid, AuxFolder f, SOID soid) throws SQLException
    {
        return Util.join(auxRoot(sid), f._name, LinkedPath.makeAuxFileName(soid));
    }

    static String nroPath(ResolvedPath path) throws SQLException
    {
        return auxPath(path.sid(), AuxFolder.NON_REPRESENTABLE, path.soid());
    }

    private String physicalPath(ResolvedPath path) throws SQLException
    {
        return physicalPath(_osutil, _nrodb, path);
    }

    static String physicalPath(IOSUtil osutil, NRODatabase nrodb, ResolvedPath path)
            throws SQLException
    {
        String s = "";
        String[] elems = path.elements();

        // iterate upwards over path components to find the first non-representable object, if any
        for (int i = elems.length - 1; i >= 0; --i) {
            String component = elems[i];
            boolean nro = nrodb.isNonRepresentable_(path.soids.get(i));
            if (nro || osutil.isInvalidFileName(component)) {
                // reached first NRO
                String base = auxPath(path.sid(), AuxFolder.NON_REPRESENTABLE, path.soids.get(i));
                return s.isEmpty() ? base : Util.join(base, s);
            } else {
                s = s.isEmpty() ? component : Util.join(component, s);
            }
        }

        return Util.join(Cfg.getRootPathNullable(path.sid()), s);
    }

    private SOID phyObject(String absPath) throws SQLException
    {
        FID fid = null;
        try {
            fid = _dr.getFID(absPath);
        }  catch (IOException e) {
            l.error("could not determine fid {}", P(absPath));
        }
        return fid != null ? _mdb.getSOID_(fid) : null;
    }
}
