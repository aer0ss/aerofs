package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Probes properties of physical filesystem
 */
public class FileSystemProber
{
    private final static Logger l = Loggers.getLogger(FileSystemProber.class);

    private final InjectableFile.Factory _factFile;

    @Inject
    public FileSystemProber(InjectableFile.Factory factFile)
    {
        _factFile = factFile;
    }

    public boolean isCaseSensitive(String auxRoot) throws IOException
    {
        try {
            // ensure probe directory is clean
            InjectableFile d = _factFile.create(auxRoot, AuxFolder.PROBE._name);
            if (d.exists()) d.deleteOrThrowIfExistRecursively();
            d.ensureDirExists();

            // probe case-sensitivity
            _factFile.create(d, "cs").createNewFile();
            return !_factFile.create(d, "CS").exists();
        } catch (IOException e) {
            l.error("failed to determine case-sensitivity of filesystem");
            throw e;
        }
    }
}
