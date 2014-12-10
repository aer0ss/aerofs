package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * Probes properties of physical filesystem
 */
public class FileSystemProber
{
    private final static Logger l = Loggers.getLogger(FileSystemProber.class);

    private final InjectableFile.Factory _factFile;

    // NB: isStricterThan assumes that all properties are restrictions and will
    // need to be updated if this ever changes
    public static enum FileSystemProperty
    {
        CaseInsensitive,
        NormalizationInsensitive
    }

    public static class ProbeException extends IOException
    {
        private static final long serialVersionUID = 0L;
        ProbeException(Throwable t) { super(t); }
    }

    @Inject
    public FileSystemProber(InjectableFile.Factory factFile)
    {
        _factFile = factFile;
    }

    public EnumSet<FileSystemProperty> probe(String auxRoot) throws ProbeException
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

            // U+1F44A FISTED HAND SIGN
            // probe support for characters outside BMP
            InjectableFile f = _factFile.create(d, probeId + "\uD83D\uDC4A");
            f.createNewFile();
            if (!f.exists()) {
                throw new FileNotFoundException("Unable to create file with unicode name");
            }

            // For the scanner to work, we need to make sure that File.list doesn't choke
            // on files with code points outside the BMP
            String[] files = d.list();
            if (!Arrays.asList(files).contains(f.getName())) {
                l.info("createNewFile and list inconsistent:\n{}", Arrays.stream(files)
                                .map(s -> s.substring(probeId.length()).chars()
                                        .mapToObj(c -> "U+" + Integer.toHexString(c))
                                        .collect(Collectors.joining(" ")))
                                .collect(Collectors.joining("\n")));
                throw new FileNotFoundException("Created files missing from listing");
            }

            // cleanup probe dir for future runs
            // NB: do not throw on failure
            d.deleteIgnoreErrorRecursively();

            return props;
        } catch (IOException e) {
            l.error("failed to probe filesystem properties", e);
            throw new ProbeException(e);
        }
    }

    public boolean isStricterThan(String path, EnumSet<FileSystemProperty> prop) throws IOException
    {
        // NB: this check only works if all properties are restrictions
        return !prop.containsAll(probe(path));
    }
}
