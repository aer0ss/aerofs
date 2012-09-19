package com.aerofs.daemon.core.linker;

import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

import javax.annotation.Nonnull;

import static com.aerofs.lib.PathObfuscator.obfuscate;

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
     * @return a new {@link PathCombo} that has the name appended to the path as the last element
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

    /**
     * @return obfuscated path string
     */
    @Override
    public String toString()
    {
        return obfuscate(_path);
    }

    @Override
    public int compareTo(PathCombo otherPC)
    {
        return _path.compareTo(otherPC._path);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PathCombo pathCombo = (PathCombo) o;

        if (!_absPath.equals(pathCombo._absPath)) return false;
        if (!_path.equals(pathCombo._path)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _absPath.hashCode();
        result = 31 * result + _path.hashCode();
        return result;
    }
}
