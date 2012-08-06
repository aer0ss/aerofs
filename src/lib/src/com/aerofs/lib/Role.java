package com.aerofs.lib;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBRole;

import javax.annotation.Nonnull;

// TODO (WW) remove this class, and use PBRole instead where this class is used. Conversions between
// strings and PBRole should be done by methods only available to UI.
public enum Role
{
    // N.B. roles with more permissions must go after those with less. This is required for
    // {@link covers(Role)} to work properly.

    VIEWER("VIEWER", PBRole.VIEWER),
    EDITOR("EDITOR", PBRole.EDITOR),
    OWNER("OWNER", PBRole.OWNER);

    private final String _description;
    private final PBRole _pb;

    /**
     * @param description the string representation of the key. Ideally it should be derived from
     * symbol names but not possible due to obfuscation. TODO use ProGuard annotations?
     */
    private Role(String description, PBRole pb)
    {
        _description = description;
        _pb = pb;
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    public String getDescription()
    {
        return _description;
    }

    public PBRole toPB()
    {
        return _pb;
    }

    public static Role fromPB(PBRole pb) throws ExBadArgs
    {
        switch (pb) {
        case EDITOR: return EDITOR;
        case VIEWER: throw new ExBadArgs("Viewers not supported");
        default: assert pb == PBRole.OWNER; return OWNER;
        }
    }

    public static Role fromString(String roleDescription) throws ExBadArgs
    {
        if (roleDescription.equals(EDITOR.toString())) {
            return EDITOR;
        } else if (roleDescription.equals(OWNER.toString())) {
            return OWNER;
        } else if (roleDescription.equals(VIEWER.toString())) {
            throw new ExBadArgs("Viewers not supported");
        } else {
            throw new ExBadArgs("invalid role description " + roleDescription);
        }
    }

    /**
     * @return true if 'this' allows all the operations allowed by {@code role}
     */
    public boolean covers(@Nonnull Role role)
    {
        assert role != null;
        return compareTo(role) >= 0;
    }

    public static Role fromOrdinal(int ordinal)
    {
        if (ordinal < 0 || ordinal >= Role.values().length) {
            throw new IllegalArgumentException("invalid role ordinal:" + ordinal);
        }

        return Role.values()[ordinal];
    }
}
