/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.db;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

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

    private final static int CAPACITY = 20;
    private final Map<T, PreparedStatementWrapper> _s = new LinkedHashMap<T, PreparedStatementWrapper>(CAPACITY, .75f, true) {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Entry<T, PreparedStatementWrapper> en)
        {
            if (size() > CAPACITY) {
                en.getValue().close();
                return true;
            } else {
                return false;
            }
        }
    };

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
