package com.aerofs.daemon.core.phy.linked.linker;

import java.util.Set;

import com.aerofs.lib.Param;
import com.google.common.collect.ImmutableSet;

/**
 * N.B. ignored files will NOT be backed up when their parent folders are deleted by remote peers
 * or expelled.
 */
public class IgnoreList
{
    private final Set<String> _set;

    IgnoreList()
    {
        _set = ImmutableSet.of(
                "Icon\r",
                ".DS_Store",
                "desktop.ini",
                "Thumbs.db");
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

        /**
         * Patterns of temporary files ignored because they interfere badly with AeroFS core logic
         *
         * NOTE: long term we probably need to use a more generic regexp based solution (probably
         * not Pattern though because it's a slow backtracking algorithm instead of a fast DFA) and
         * load the list of excluded pattern from a text file so that power users can tweak the
         * ignore list
         */

        // GEdit creates temporary .goutputstream-XXXXXX and copies them over the target file when
        // saving which pollutes the DB, causes useless network overhead and creates linker conflict
        // so we simply ignore these files
        if (name.startsWith(".goutputstream-")) return true;

        // Kate creates temporary .${filename}.kate-swp files to hold the temporary state of a file
        // being edited and writes it to disk on pretty much every keystroke which pollutes the DB,
        // creates an inordinate amount of uninteresting activity log entries and significant
        // network overhead so we ignore these files
        if (name.charAt(0) == '.' && name.endsWith(".kate-swp")) return true;

        return Linker.isInternalFile(name) || _set.contains(name);
    }
}
