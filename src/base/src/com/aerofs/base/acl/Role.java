package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBRole;

import javax.annotation.Nonnull;

/**
 * Note that this enum's structure is very similar to SharedFolderState.
 */
public enum Role
{
    // N.B. roles with more permissions must go after those with less. This is required for
    // {@link covers(Role)} to work properly.

    // N.B. the ordinals of these enums are stored in the databases.
    // Be VERY CAREFUL when shifting them!

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
        case VIEWER: return VIEWER;
        case EDITOR: return EDITOR;
        case OWNER: return OWNER;
        // Since the intput comes from external soruces, we should not throw runtime exceptions
        // which may crash the process.
        default: throw new ExBadArgs("Unknown role: " + pb.toString());
        }
    }

    public static Role fromString(String roleDescription) throws ExBadArgs
    {
        for (Role role : values()) {
            if (roleDescription.equals(role.getDescription())) return role;
        }
        // Since the intput comes from external soruces, we should not throw runtime exceptions
        // which may crash the process.
        throw new ExBadArgs("invalid role description " + roleDescription);
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
        assert ordinal >= 0 && ordinal < Role.values().length;
        return Role.values()[ordinal];
    }
}
