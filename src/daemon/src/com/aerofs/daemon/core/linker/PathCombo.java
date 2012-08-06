package com.aerofs.daemon.core.linker;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.LinkedList;
import java.util.Set;

/**
 * This class combines Path and its corresponding string-type path. The former
 * is used for locating objects in the database, the latter is for locating in
 * the filesystem. The class is intended for avoiding frequent conversion between
 * the two path formats.
 */
public class PathCombo
{
    public final String _absPath;      // the absolute filesystem path
    public final Path _path;           // the path relative to absAnchorRoot

    /**
     *  @param absPath an absolute file system path. N.B. it is assumed to point to an object
     *                 under the current absAnchorRoot.
     */
    public PathCombo(CfgAbsRootAnchor cfgAbsRootAnchor, String absPath)
    {
        String root = cfgAbsRootAnchor.get();
        _path = Path.fromAbsoluteString(root, absPath);
        _absPath = absPath;
    }

    public PathCombo(CfgAbsRootAnchor cfgAbsRootAnchor, Path path)
    {
        String root = cfgAbsRootAnchor.get();
        _path = path;
        _absPath = path.toAbsoluteString(root);
    }

    /**
     * @return a new {@link PathCombo} that has the name appended to the path
     * as the last element
     */
    public PathCombo append(String name)
    {
        return new PathCombo(Util.join(_absPath, name), _path.append(name));
    }

    private PathCombo(String absPath, Path path)
    {
        _absPath = absPath;
        _path = path;
    }

    @Override
    public String toString()
    {
        return _path.toString();
    }

    public static String toLogString(Path path)
    {
        return toLogString(path.elements());
    }

    public static String toLogString(String path)
    {
        LinkedList<String> names = Lists.newLinkedList();
        File f = new File(path);

        // Root directory has an empty filename
        while (f != null && !f.getName().isEmpty()) {
            names.addFirst(f.getName());
            f = f.getParentFile();
        }

        String[] elements = new String[names.size()];
        names.toArray(elements);

        return toLogString(elements);
    }

    public static String toLogString(Set<String> paths)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        String separator = "";
        for (String path : paths) {
            sb.append(separator);
            separator = ", ";
            sb.append(toLogString(path));
        }
        sb.append("]");

        return sb.toString();
    }

    public static String toLogString(String[] elements)
    {
        StringBuilder sb = new StringBuilder();

        if (elements.length == 0) {
            sb.append("/");
        }

        for (String name : elements) {
            sb.append('/');
            sb.append(Cfg.staging() ? name : Util.crc32(name));
        }

        return sb.toString();
    }
}
