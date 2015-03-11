package com.aerofs.daemon.core.update;

import com.aerofs.ids.SID;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;

import java.io.File;
import java.util.Map;

/**
 * Changes to prefix storage to bring about incremental prefix hashing required
 * all old prefixes to be discarded.
 */
public class DPUTCleanupPrefixes implements IDaemonPostUpdateTask
{
    @Override
    public void run() throws Exception {
        for (Map.Entry<SID, String> e: Cfg.getRoots().entrySet()) {
            String auxRoot = Cfg.absAuxRootForPath(e.getValue(), e.getKey());
            File prefix = new File(auxRoot, LibParam.AuxFolder.PREFIX._name);
            FileUtil.deleteIgnoreErrorRecursively(prefix);
        }
    }
}