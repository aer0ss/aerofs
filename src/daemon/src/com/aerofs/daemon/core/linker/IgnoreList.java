package com.aerofs.daemon.core.linker;

import java.util.Set;

import com.aerofs.lib.C;
import com.google.common.collect.Sets;

/**
 * N.B. ignored files will NOT be backed up when their parent folders are deleted by remote peers
 * or expelled.
 */
public class IgnoreList
{
    private final Set<String> _set;

    IgnoreList()
    {
        _set = Sets.newHashSet();
        _set.add("Icon\r");
        _set.add(".DS_Store");
        _set.add("desktop.ini");
        _set.add(C.SHARED_FOLDER_TAG);
    }

    /**
     * @param name the name (not path) of a physical object
     */
    public boolean isIgnored_(String name)
    {
        // The Java File API transparently converts filenames as bytestrings into
        // Strings using the platform encoding.  If the filename's bytestring
        // is invalid under that encoding (usually UTF-8), then String replaces
        // that byte with a Unicode replacement character.
        //
        // When this path is passed to Scanner will fail to find the file when that
        // String is passed to getFID, which causes a full rescan.  This causes an
        // infinite rescan loop which makes no forward progress syncing.
        //
        // This workaround ignores such files and folders so that syncing can
        // progress.
        // Addresses aerofs-590.
        if (name.indexOf('\ufffd') != -1) return true;

        return _set.contains(name);
    }
}
