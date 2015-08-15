package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.BaseUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Pattern;

public class RegexFilter {
    private static final Logger l = LoggerFactory.getLogger(RegexFilter.class);
    private final CfgAbsRTRoot _cfgAbsRtRoot;
    private final Pattern _pattern;

    @Inject
    public RegexFilter(CfgAbsRTRoot cfgAbsRTRoot) {
        _cfgAbsRtRoot = cfgAbsRTRoot;
        _pattern = loadPattern();
    }

    public Pattern getPattern() { return _pattern; }

    private Pattern loadPattern()
    {
        String pattern;
        try {
            pattern = BaseUtil.utf2string(ByteStreams.toByteArray(
                    new FileInputStream(new File(Util.join(_cfgAbsRtRoot.get(), "regex.txt"))))).trim();
            l.info("Loaded pattern from regex.txt: {}", pattern);
            return Pattern.compile(pattern);
        } catch (FileNotFoundException ignore) {
            // ignored. Not all installations have regex.txt.
            l.info("No regex.txt found.");
        } catch (IOException e) {
            l.warn("Failed to load user regex: {}", e.getMessage());
        }
        return null;
    }
}
