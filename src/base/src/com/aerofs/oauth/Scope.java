/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.oauth;

import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * OAuth scopes supported by the API
 *
 * Some scopes are "qualifiable", i.e. they can be restricted to a specific
 * subset of the data controlled by the scope. Currently, a qualifiable scope
 * can be restricted to a particular RestObject (which includes child objects,
 * in the case of a folder).
 */
public enum Scope
{
    READ_FILES("files.read", true),
    WRITE_FILES("files.write", true),
    READ_ACL("acl.read", true),
    WRITE_ACL("acl.write", true),
    MANAGE_INVITATIONS("acl.invitations", false),
    READ_USER("user.read", false),
    WRITE_USER("user.write", false),
    MANAGE_PASSWORD("user.password", false),
    ORG_ADMIN("organization.admin", false),
    APPDATA("files.appdata", false),
    LINKSHARE("linksharing", false)
    ;

    private final String name;
    private final boolean qualifiable;

    Scope(String name, boolean qualifiable)
    {
        this.name = name;
        this.qualifiable = qualifiable;
    }

    private final static Map<String, Scope> NAMES;
    static {
        NAMES = Maps.newHashMap();
        for (Scope scope : values()) checkState(NAMES.put(scope.name, scope) == null);
    }

    public static @Nullable Scope fromName(String name)
    {
        return NAMES.get(name);
    }

    public static boolean isQualifiable(Scope s) { return s.qualifiable; }
    public static boolean requiresAdmin(Scope s) { return ORG_ADMIN.equals(s); }
}
