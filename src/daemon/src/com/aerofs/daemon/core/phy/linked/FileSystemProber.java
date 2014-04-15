package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.UniqueID;
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

    // NB: isStricterThan assumes that all properties are restrictions and will
    // need to be updated if this ever changes
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
            // ensure probe directory exists
            InjectableFile d = _factFile.create(auxRoot, AuxFolder.PROBE._name);
            d.ensureDirExists();

            // use a random UUID for each probe in case the probe dir isn't clean
            String probeId = UniqueID.generate().toStringFormal();

            // probe case-sensitivity
            _factFile.create(d, probeId + "cs").createNewFile();
            if (_factFile.create(d, probeId + "CS").exists()) {
                props.add(FileSystemProperty.CaseInsensitive);
            }

            // probe normalization-sensitivity (NFC/NFD)
            _factFile.create(d, probeId + "\u00e9").createNewFile();
            if (_factFile.create(d, probeId + "e\u0301").exists()) {
                props.add(FileSystemProperty.NormalizationInsensitive);
            }

            // cleanup probe dir for future runs
            // NB: do not throw on failure
            d.deleteIgnoreErrorRecursively();

            return props;
        } catch (IOException e) {
            l.error("failed to probe filesystem properties", e);
            throw e;
        }
    }

    public boolean isStricterThan(String path, EnumSet<FileSystemProperty> prop) throws IOException
    {
        // NB: this check only works if all properties are restrictions
        return !prop.containsAll(probe(path));
    }
}
