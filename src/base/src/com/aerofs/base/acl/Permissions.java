package com.aerofs.base.acl;

import com.aerofs.proto.Common.PBPermission;
import com.aerofs.proto.Common.PBPermissions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableBiMap.Builder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;

public class Permissions implements Comparable<Permissions>
{
    public final static String RESTRICTED_EXTERNAL_SHARING
            = "sharing_rules.restrict_external_sharing";

    public enum Permission
    {
        // NB: the order of these values is used to sort Roles
        //
        // See docs/design/acl.md for more details
        MANAGE(PBPermission.MANAGE, "manage members"),
        WRITE(PBPermission.WRITE, "modify files");

        private static final int ALL = union(Arrays.asList(values()));

        private final PBPermission _pb;
        private final String _description;

        private Permission(PBPermission pb, String description)
        {
            _pb = pb;
            _description = description;
        }

        public int flag()
        {
            return 1 << _pb.getNumber();
        }

        public String description()
        {
            return _description;
        }

        public PBPermission toPB()
        {
            return _pb;
        }

        static int union(Iterable<Permission> permissions)
        {
            int r = 0;
            for (Permission f : permissions) r |= f.flag();
            return r;
        }

        public static Permission fromPB(PBPermission p)
        {
            return valueOf(p.name());
        }
    }

    private final int _bitmask;
    private final EnumSet<Permission> _set = EnumSet.noneOf(Permission.class);

    public final static ImmutableBiMap<Permissions, String> ROLE_NAMES = buildRoleNames();

    // for convenience
    public final static Permissions VIEWER = allOf();
    public final static Permissions EDITOR = allOf(Permission.WRITE);
    public final static Permissions OWNER = allOf(Permission.WRITE, Permission.MANAGE);

    private Permissions(Collection<PBPermission> permissions)
    {
        for (PBPermission p : permissions) _set.add(Permission.fromPB(p));
        _bitmask = Permission.union(_set);
    }

    private Permissions(int permissions)
    {
        Preconditions.checkArgument((permissions & ~Permission.ALL) == 0);
        _bitmask = permissions;
        for (Permission p : Permission.values()) if ((permissions & p.flag()) != 0) _set.add(p);
    }

    public PBPermissions toPB()
    {
        PBPermissions.Builder bd = PBPermissions.newBuilder();
        for (Permission p : _set) bd.addPermission(p.toPB());
        return bd.build();
    }

    public int bitmask()
    {
        return _bitmask;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && o instanceof Permissions
                                     && ((Permissions)o)._bitmask == _bitmask);
    }

    @Override
    public int hashCode()
    {
        return _bitmask;
    }

    @Override
    public int compareTo(@Nonnull Permissions o)
    {
        for (Permission p : Permission.values()) {
            int c = (_set.contains(p) ? 1 : 0) - (o._set.contains(p) ? 1 : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    @Override
    public String toString()
    {
        StringBuilder bd = new StringBuilder("Permissions[" + _bitmask +"](READ");
        for (Permission p : _set) bd.append(", ").append(p.name());
        bd.append(")");
        return bd.toString();
    }

    public Permission[] toArray()
    {
        return _set.toArray(new Permission[_set.size()]);
    }

    // this smells, ideally we should get mapping of Permissions to role names
    // from the configuration
    private static ImmutableBiMap<Permissions, String> buildRoleNames()
    {
        Builder<Permissions, String> bd = ImmutableBiMap.builder();
        bd.put(Permissions.allOf(Permission.WRITE, Permission.MANAGE), "Owner");
        if (getBooleanProperty(RESTRICTED_EXTERNAL_SHARING, false)) {
            bd.put(Permissions.allOf(Permission.MANAGE), "Manager");
        }
        bd.put(Permissions.allOf(Permission.WRITE), "Editor");
        bd.put(Permissions.allOf(), "Viewer");
        return bd.build();
    }

    public String roleName()
    {
        String roleName = ROLE_NAMES.get(this);
        return roleName != null ? roleName : roleDescription();
    }

    public static @Nullable Permissions fromRoleName(String roleName)
    {
        return ROLE_NAMES.inverse().get(roleName);
    }

    public String roleDescription()
    {
        StringBuilder bd = new StringBuilder("download");
        if (_set.isEmpty()) return bd.append(" only").toString();
        int n = _set.size();
        if (covers(Permission.WRITE)) bd.append(n == 1 ? " and " : ", ").append("upload");
        if (covers(Permission.MANAGE)) bd.append(n == 1 ? " and " : ", and ").append("manage");
        return bd.toString();
    }

    public Permissions minus(Permission p)
    {
        return fromBitmask(bitmask() & ~p.flag());
    }

    public static Permissions allOf(Permission... permissions)
    {
        return new Permissions(Permission.union(Arrays.asList(permissions)));
    }

    public static Permissions fromPB(PBPermissions pb)
    {
        return new Permissions(pb.getPermissionList());
    }

    public static Permissions fromBitmask(int permissions)
    {
        return new Permissions(permissions);
    }

    /**
     * @return true if 'this' allows all the operations allowed by {@paramref permission}
     */
    public boolean covers(@Nonnull Permission permission)
    {
        return (_bitmask & Preconditions.checkNotNull(permission).flag()) != 0;
    }

    /**
     * @return true if 'this' allows all the operations allowed by {@paramref permissions}
     */
    public boolean covers(Permissions permissions)
    {
        return (_bitmask & permissions._bitmask) == permissions._bitmask;
    }
}
