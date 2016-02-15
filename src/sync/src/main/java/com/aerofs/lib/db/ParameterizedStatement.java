/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.db;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Sometimes the SQL query depends on a parameter that cannot simply be bound as a prepared
 * statement variable. This happens for instance when the name of the table depends on the
 * parameter.
 */
public class ParameterizedStatement<T>
{
    @FunctionalInterface
    public interface StatementProvider<T>
    {
        String get(T t);
    }

    private final StatementProvider<T> _provider;

    // TODO: LRU cache?
    private final Map<T, PreparedStatementWrapper> _s = Maps.newHashMap();

    public ParameterizedStatement(StatementProvider<T> provider)
    {
        _provider = provider;
    }

    public PreparedStatementWrapper get(T t)
    {
        PreparedStatementWrapper psw = _s.get(t);
        if (psw == null) {
            psw = new PreparedStatementWrapper(_provider.get(t));
            _s.put(t, psw);
        }
        return psw;
    }
}
