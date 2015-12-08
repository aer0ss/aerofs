/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.net.ResponseStream;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;


public class ContentUpdater {
    private final static Logger l = Loggers.getLogger(ContentUpdater.class);

    private final TransManager _tm;
    private final ContentProvider _provider;
    private final IPathResolver _resolver;
    private final IPhysicalStorage _ps;
    private final Causality _causality;
    private final ContentReceiver _rc;
    private final PrefixVersionControl _pvc;

    @Inject
    public ContentUpdater(TransManager tm, ContentProvider provider, IPhysicalStorage ps,
                          ContentReceiver rc, Causality causality,  IPathResolver resolver,
                          PrefixVersionControl pvc) {
        _tm = tm;
        _provider = provider;
        _ps = ps;
        _rc = rc;
        _causality = causality;
        _resolver = resolver;
        _pvc = pvc;
    }

    public static class ReceivedContent {
        final long mtime;
        final long length;
        final long prefixLength;
        final Version vRemote;
        final @Nullable ContentHash hash;
        final long lts;

        ReceivedContent(long mtime, long length, long prefixLength, Version vRemote,
                        @Nullable ContentHash hash, long lts) {
            this.mtime = mtime;
            this.length = length;
            this.prefixLength = prefixLength;
            this.vRemote = vRemote;
            this.hash = hash;
            this.lts = lts;
        }
    }

    public void processContentResponse_(SOID soid, ReceivedContent content, ResponseStream rs,
                                        CausalityResult cr, Token tk) throws Exception {
        // This is the branch to which the update should be applied
        // FIXME: the target branch should be determined once the download is complete
        // and the content hash should be used instead of the kidx to distinguish prefixes
        // NB: this will have to wait until phoenix is shipped and legacy is burned
        SOKID targetBranch = new SOKID(soid, cr._kidx);

        IPhysicalPrefix prefix = null;
        ContentHash h;
        IPhysicalFile matchingContent = cr._hash != null
                ? _provider.fileWithMatchingContent(soid, cr._hash) : null;

        if (cr._avoidContentIO || (matchingContent != null &&
                matchingContent.sokid().equals(targetBranch))) {
            l.debug("content already there, avoid I/O altogether");
            // no point doing any file I/O...
            h = cr._hash;
        } else {
            prefix = _ps.newPrefix_(targetBranch, null);
            h = _rc.download_(prefix, rs, targetBranch, content.vRemote, content.length,
                    content.prefixLength, cr._hash, matchingContent, tk);
        }

        try (Trans t = _tm.begin_()) {
            _causality.updateVersion_(targetBranch, content, cr, t);
            if (prefix != null) {
                ResolvedPath path = _resolver.resolveNullable_(soid);
                if (path == null) throw new ExNotFound("no path to " + soid);
                IPhysicalFile pf = _ps.newFile_(path, cr._kidx);
                _provider.apply_(prefix, pf, content.mtime, h, t);
                _pvc.deletePrefixVersion_(soid, cr._kidx, t);
            }
            t.commit_();
        } catch (Exception | Error e) {
            l.debug("rollback triggered ", e);
            throw e;
        }
        l.info("{} ok {}", rs.ep(), soid);
    }
}
