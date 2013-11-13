package com.aerofs.daemon.core.phy.linked;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;

/**
 * Helper class to manipulate path within linked storage
 */
public class LinkedPath
{
    // true if physical file in linked root (as opposed to aux folder)
    private enum Type {
        Representable,      // regular object or child of a NRO
        NonRepresentable,   // Non-Representable Object
        Auxiliary           // prefix, conflict branch, ...
    }

    private final Type _type;

    // null only when applying prefixes
    public @Nullable final Path virtual;
    // absolute physical path
    public final String physical;

    /**
     * private ctor. Use one of the factory methods below.
     */
    private LinkedPath(Type type, @Nullable Path virtual, String physical)
    {
        this._type = type;
        this.virtual = virtual;
        this.physical = physical;
    }

    public static LinkedPath representable(Path virtual, String physicalRoot)
    {
        // IMPORTANT: use Util.join instead of Path.toStringRelative
        // as getFID will barf on Windows if it gets forward slashes
        return new LinkedPath(Type.Representable, virtual,
                Util.join(physicalRoot, Util.join(virtual.elements())));
    }

    public static LinkedPath representableChildOfNonRepresentable(Path virtual, String physical)
    {
        return new LinkedPath(Type.Representable, virtual, physical);
    }

    public static LinkedPath nonRepresentable(Path virtual, String physical)
    {
        return new LinkedPath(Type.NonRepresentable, virtual, physical);
    }

    public static LinkedPath auxiliary(Path virtual, String physical)
    {
        return new LinkedPath(Type.Auxiliary, virtual, physical);
    }


    public boolean isRepresentable()
    {
        return _type == Type.Representable;
    }

    public boolean isNonRepresentable()
    {
        return _type == Type.NonRepresentable;
    }

    @Override
    public String toString()
    {
        return String.format("(%s, %s)", virtual, physical);
    }


    public static String makeAuxFilePrefix(SIndex sidx)
    {
        return Integer.toString(sidx.getInt()) + '-';
    }

    public static String makeAuxFileName(SOID soid)
    {
        return makeAuxFilePrefix(soid.sidx()) + soid.oid().toStringFormal();
    }

    public static String makeAuxFileName(SOKID sokid)
    {
        return makeAuxFileName(sokid.soid()) + '-' + sokid.kidx();
    }
}
