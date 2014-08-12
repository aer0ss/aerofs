/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.daemon.core.phy.linked.FileSystemProber;
import com.aerofs.daemon.core.phy.linked.FileSystemProber.FileSystemProperty;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import com.aerofs.daemon.core.phy.linked.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExFileNoPerm;
import com.aerofs.lib.ex.ExFileNotFound;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A linker root is defined by the absolute path at which it is anchored and the SID attached to it.
 *
 * Each linker root has its own {@code ScanSessionQueue}.
 */
public class LinkerRoot
{
    private static final Logger l = Loggers.getLogger(LinkerRoot.class);

    public static class Factory
    {
        private final MightCreate _mc;
        private final MightDelete _md;
        private final ScanSessionQueue.Factory _factSSQ;
        private final TransManager _tm;
        private final IDeletionBuffer _globalBuffer;
        private final FileSystemProber _prober;

        @Inject
        public Factory(ScanSessionQueue.Factory factSSQ, MightCreate mc, MightDelete md,
                IDeletionBuffer globalBuffer, TransManager tm, FileSystemProber prober)
        {
            _mc = mc;
            _md = md;
            _factSSQ = factSSQ;
            _globalBuffer = globalBuffer;
            _tm = tm;
            _prober = prober;
        }

        public LinkerRoot create_(SID sid, String absRoot) throws IOException
        {
            return new LinkerRoot(this, sid, absRoot);
        }
    }

    private final SID _sid;
    private final String _absRootAnchor;

    final OIDGenerator _og;
    int _watchId;

    /**
     * needed for safe and efficient iteration of {@code LinkedRootMap} contents by {@code Linker},
     * itself needed to correctly handle ScanCompletionCallback when scanning multiple roots
     *
     * Also necessary to prevent in-flight scan/mcn/mdn events from being processed after the root
     * is removed (which would most likely end up triggering an assert failure...)
     */
    boolean _removed;

    private final Factory _f;
    private final ScanSessionQueue _ssq;

    private final EnumSet<FileSystemProperty> _properties;

    private LinkerRoot(Factory f, SID sid, String absRootAnchor) throws IOException
    {
        _f = f;
        _sid = sid;
        _absRootAnchor = absRootAnchor;
        _ssq = _f._factSSQ.create_(this);
        _og = new OIDGenerator(sid, absRootAnchor);
        _properties = _f._prober.probe(absAuxRoot());
    }

    public SID sid()
    {
        return _sid;
    }

    public String absRootAnchor()
    {
        return _absRootAnchor;
    }

    public String absAuxRoot()
    {
        return Cfg.absAuxRootForPath(_absRootAnchor, _sid);
    }

    public int watchId()
    {
        return _watchId;
    }

    public boolean wasRemoved()
    {
        return _removed;
    }

    public OIDGenerator OIDGenerator()
    {
        return _og;
    }

    public EnumSet<FileSystemProperty> properties()
    {
        return EnumSet.copyOf(_properties);
    }

    boolean isPhysicallyEquivalent(String a, String b)
    {
        if (_properties.contains(FileSystemProperty.NormalizationInsensitive)) {
            a = Normalizer.normalize(a, Form.NFC);
            b = Normalizer.normalize(b, Form.NFC);
        }
        return _properties.contains(FileSystemProperty.CaseInsensitive)
                ? a.equalsIgnoreCase(b)
                : a.equals(b);
    }

    @Override
    public String toString()
    {
        return "LinkerRoot(" + _sid + ", " + _absRootAnchor + ", " + _properties + ")";
    }

    public void stageDeletion(Trans t)
    {
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                _removed = true;
            }
        });
    }

    public void mightDelete_(String absPath)
    {
        if (_removed) {
            l.info("ignored MDN for unlinked root {}", absPath);
            return;
        }
        PathCombo pc = new PathCombo(_sid, _absRootAnchor, absPath);
        try {
            _f._md.mightDelete_(pc, _f._globalBuffer);
        } catch (Exception e) {
            // On any exception, perform a full scan
            l.warn("full scan triggered by " + Util.e(e));
            _ssq.scanAfterDelay_(Collections.singleton(_absRootAnchor), true);
        }
    }

    public void mightCreate_(String absPath)
    {
        if (_removed) {
            l.info("ignored MCN for unlinked root {}", absPath);
            return;
        }
        PathCombo pc = new PathCombo(_sid, _absRootAnchor, absPath);
        try {
            MightCreate.Result res;
            Trans t = _f._tm.begin_();
            try {
                res = _f._mc.mightCreate_(pc, _f._globalBuffer, _og, t);
                t.commit_();
            } catch (ExFileNotFound e) {
                // We may get a not found exception if a file was created and deleted or moved very
                // soon thereafter, and we didn't get around to checking it out until it was already
                // gone. We simply ignore the error in this situation to avoid frequent rescans
                // and thus cpu hogging when editors create and delete/move temporary files.
                l.warn("ignored by MCN: " + Util.e(e, ExFileNotFound.class));
                return;
            } catch (ExFileNoPerm e) {
                // We can also safely ignore files which we have no permission to access.
                // It's not like we can sync them anyway.
                l.warn("ignored by MCN: " + Util.e(e, ExFileNoPerm.class));
                return;
            } catch (ExNotFound e) {
                // Same conditon as for ExFileNotFound exception
                l.warn("ignored by MCN: " + Util.e(e, ExNotFound.class));
                return;
            } finally {
                t.end_();
            }
            if (res == Result.NEW_OR_REPLACED_FOLDER) {
                // We need to scan subdirectories of new folders because they could have data
                // placed in them faster than we can register a watch on Linux.
                _ssq.scanImmediately_(Collections.singleton(pc._absPath), true);
            }
        } catch (Exception e) {
            // On any exception, perform a full scan.
            l.warn("full scan triggered by " + Util.e(e));
            _ssq.scanAfterDelay_(Collections.singleton(_absRootAnchor), true);
        }
    }

    public void scanImmediately_(Set<String> batch, boolean recurse)
    {
        _ssq.scanImmediately_(batch, recurse);
    }

    void recursiveScanImmediately_(final ScanCompletionCallback callback)
    {
        if (_removed) {
            _og.onScanCompletion_();
            callback.done_();
        } else {
            _ssq.recursiveScanImmediately_(Collections.singleton(_absRootAnchor),
                    new ScanCompletionCallback() {
                @Override
                public void done_()
                {
                    _og.onScanCompletion_();
                    callback.done_();
                }
            });
        }
    }
}