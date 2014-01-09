package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Probes properties of physical filesystem
 */
public class FileSystemProber
{
    private final static Logger l = Loggers.getLogger(FileSystemProber.class);

    private final InjectableFile.Factory _factFile;

    public enum FileSystemProperty
    {
        CaseInsensitive,
        NormalizationInsensitive
    }

    @Inject
    public FileSystemProber(InjectableFile.Factory factFile)
    {
        _factFile = factFile;
    }

    public EnumSet<FileSystemProperty> probe(String auxRoot) throws IOException
    {
        EnumSet<FileSystemProperty> props = EnumSet.noneOf(FileSystemProperty.class);

        try {
            // ensure probe directory is clean
            InjectableFile d = _factFile.create(auxRoot, AuxFolder.PROBE._name);
            if (d.exists()) d.deleteOrThrowIfExistRecursively();
            d.ensureDirExists();

            // probe case-sensitivity
            _factFile.create(d, "cs").createNewFile();
            if (_factFile.create(d, "CS").exists()) {
                props.add(FileSystemProperty.CaseInsensitive);
            }

            // probe normalization-sensitivity (NFC/NFD)
            _factFile.create(d, "\u00e9").createNewFile();
            if (_factFile.create(d, "e\u0301").exists()) {
                props.add(FileSystemProperty.NormalizationInsensitive);
            }

            return props;
        } catch (IOException e) {
            l.error("failed to probe filesystem properties");
            throw e;
        }
    }
}
