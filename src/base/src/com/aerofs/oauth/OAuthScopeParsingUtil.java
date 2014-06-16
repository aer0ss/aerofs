/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.oauth;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.RestObject;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class OAuthScopeParsingUtil
{
    private static class QualifiedScope
    {
        Scope scope;
        RestObject object;

        public QualifiedScope(@Nonnull Scope scope, @Nullable RestObject object)
        {
            this.scope = scope;
            this.object = object;
        }
    }

    /**
     * Does nothing if the string contains a single valid scope, e.g. "user.write" or
     * "files.read:{RestObject}". Throws ExFormatError otherwise.
     */
    public static void validateSingleScope(String scope)
        throws ExFormatError
    {
        qualifiedScopeFromString(scope);
    }

    /**
     * Does nothing if the string contains a set of valid comma-delimited scopes, or throws
     * ExFormatError.
     */
    public static void validateScopes(String scopes)
            throws ExFormatError
    {
        Set<String> s = Sets.newHashSet(scopes.split(","));
        validateScopes(s);
    }

    /**
     * Does nothing if the arg contains a set of valid scopes, or throws ExFormatError.
     */
    public static void validateScopes(Set<String> scopes)
            throws ExFormatError
    {
        for (String scope : scopes) validateSingleScope(scope);
    }

    public static Map<Scope, Set<RestObject>> parseScopes(Set<String> strings)
    {
        QualifiedScope qualifiedScope;
        Map<Scope, Set<RestObject>> parsed = Maps.newHashMap();

        for (String string : strings)
        {
            try {
                qualifiedScope = qualifiedScopeFromString(string);
            } catch (ExFormatError exFormatError) {
                continue;
            }
            if (qualifiedScope.object == null) {
                parsed.put(qualifiedScope.scope, Collections.<RestObject>emptySet());
            } else {
                Set<RestObject> setForScope = parsed.get(qualifiedScope.scope);
                if (setForScope == null) {
                    setForScope = Sets.newHashSet();
                    parsed.put(qualifiedScope.scope, setForScope);
                }
                setForScope.add(qualifiedScope.object);
            }
        }
        return parsed;
    }

    private static QualifiedScope qualifiedScopeFromString(String string)
            throws ExFormatError
    {
        Scope scope;
        String[] s = string.split(":");
        scope = Scope.fromName(s[0]);
        if (scope == null) throw new ExFormatError("unknown scope type: " + s[0]);
        if (s.length == 1) {
            return new QualifiedScope(scope, null);
        } else if (s.length == 2) {
            try {
                return new QualifiedScope(scope, RestObject.fromString(s[1]));
            } catch (IllegalArgumentException e) {
                throw new ExFormatError(e);
            }
        } else {
            throw new ExFormatError("invalid scope");
        }
    }
}
