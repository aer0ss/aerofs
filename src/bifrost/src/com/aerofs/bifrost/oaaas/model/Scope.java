/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.bifrost.oaaas.model;

/**
 * Utility class since Scope is not actually part of the data model as represented in the DB.
 */
public class Scope
{
    // FIXME: See AuthToken. Consider moving that class elsewhere so we can both depend on it?

    /**
     * True if the scope name being requested grants and/or requires org-admin privilege.
     */
    public static boolean isPrivilegedScope(String scopeName)
    {
        return scopeName.equals("organization.admin");
    }
}
