package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.ids.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * This class combines Path and its corresponding string-type path. The former
 * is used for locating objects in the database, the latter is for locating in
 * the filesystem. The class is intended for avoiding frequent conversion between
 * the two path formats.
 */
public class PathCombo implements Comparable<PathCombo>
{
    public final @Nonnull String _absPath;      // the absolute filesystem path
    public final @Nonnull Path _path;           // the path relative to absAnchorRoot

    /**
     *  @param absPath an absolute file system path. N.B. it is assumed to point to an object
     *                 under the current absAnchorRoot.
     */
    public PathCombo(SID sid, String absRootAnchor, String absPath)
    {
       _path = Path.fromAbsoluteString(sid, absRootAnchor, absPath);
        _absPath = absPath;
    }

    public PathCombo(String absRootAnchor, Path path)
    {
        _path = path;
        _absPath = path.toAbsoluteString(absRootAnchor);
    }

    /**
     * @return a new {@link PathCombo} that has the name appended to the path as the last element
     */
    public PathCombo append(String name)
    {
        return new PathCombo(_path.append(name), Util.join(_absPath, name));
    }

    private PathCombo(Path path, String absPath)
    {
        _absPath = absPath;
        _path = path;
    }

    /**
     * @return path string
     */
    @Override
    public String toString()
    {
        return _path.toString();
    }

    @Override
    public int compareTo(PathCombo otherPC)
    {
        return _path.compareTo(otherPC._path);
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _path.equals(((PathCombo) o)._path));
    }

    @Override
    public int hashCode()
    {
        return _path.hashCode();
    }

    public PathCombo parent()
    {
        return new PathCombo(_path.removeLast(), new File(_absPath).getParent());
    }
}
