/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.unsyncablefiles;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject;

import javax.annotation.Nonnull;

/**
 * This class represents the GUI's model of an unsyncable file, aka non-representable object.
 *
 * The key consideration when working with an UnsyncableFile is as follows: the path is provided by
 * the daemon and it may contain non-printable unicode characters.
 */
public class UnsyncableFile
{
    public final @Nonnull Path _path;
    public final @Nonnull String _reason;

    private UnsyncableFile(@Nonnull Path path, @Nonnull String reason)
    {
        _path = path;
        _reason = reason;
    }

    /**
     * @throws ExBadArgs if the path to the object's store cannot be resolved.
     */
    public static UnsyncableFile fromPB(@Nonnull PBNonRepresentableObject nro) throws ExBadArgs
    {
        return new UnsyncableFile(Path.fromPB(nro.getPath()), nro.getReason());
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o instanceof UnsyncableFile &&
                _path.equals(((UnsyncableFile)o)._path));
    }

    @Override
    public int hashCode()
    {
        return _path.hashCode();
    }
}
