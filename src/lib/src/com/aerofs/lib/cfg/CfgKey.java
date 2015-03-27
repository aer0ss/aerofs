package com.aerofs.lib.cfg;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.Set;

public class CfgKey {

    private static Set<CfgKey> _set = Sets.newHashSet();

    private final String _str;
    private @Nullable final String _defaultValue;

    CfgKey(String str, @Nullable String defaultValue) {
        _str = str;
        _defaultValue = defaultValue;
        _set.add(this);
    }

    CfgKey(String str, long defaultValue) {
        this(str, Long.toString(defaultValue));
    }

    CfgKey(String str, boolean defaultValue) {
        this(str, Boolean.toString(defaultValue));
    }

    @Override
    public String toString() {
        return _str;
    }

    public String keyString() {
        return _str;
    }

    public String defaultValue() {
        return _defaultValue;
    }

    @Override
    public int hashCode() {
        return _str.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj != null) && (obj instanceof CfgKey) && _str.equals(((CfgKey)obj).keyString());
    }


    public static ImmutableSet<CfgKey> getAllConfigKeys()
    {
        return ImmutableSet.copyOf(_set);
    }
}
