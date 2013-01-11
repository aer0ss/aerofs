/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

import java.util.Comparator;

/**
 * Implemented by classes that want to be identified and ranked relative to their
 * siblings
 */
public interface IIdentifier
{
    /**
     * Identifier
     *
     * @return <em>constant</em> identifier for the implementer. This should
     * probably be unique, but it is the implementer's responsibility to
     * guarantee this.
     */
    String id();

    /**
     * Ranking relative to siblings
     *
     * @return <em>constant</em> ranking relative to siblings for the implementer.
     * This should also probably be unique, but again, it is the implementer's
     * responsibility to guarantee this.
     */
    int rank();

    /**
     * A concrete implementation of {@link IIdentifier} that defines comparison
     * and equality based <em>only</em> on the value returned by <code>rank()</code>.
     * Given two instances <code>i1</code> and <code>i2</code> of
     * <code>BasicIdentifier</code>, if <code>i1</code> has a lower <code>rank()</code>
     * value than <code>i2</code>, then <code>i1</code> is higher-ranked than
     * <code>i2</code>.
     */
    public static class BasicIdentifier implements IIdentifier
    {
        /**
         * Constructor
         *
         * @param id Idenitfier to initialize to. Once set, it cannot be changed.
         * @param rank Ranking relative to siblings. Once set, it cannot be changed.
         */
        public BasicIdentifier(String id, int rank)
        {
            _id = id;
            _rank = rank;
        }

        @Override
        public String id()
        {
            return _id;
        }

        @Override
        public int rank()
        {
            return _rank;
        }

        @Override
        public int hashCode()
        {
            return new Integer(_rank).hashCode(); // yeah, yeah, getting called all the time, but I doubt this is an issue
        }

        /**
         * Equality is determined in two phases:
         * <ol>
         *     <li><code>false</code> if this is not an instance of
         *         <code>BasicIdentifier</code></li>
         *     <li><code>false</code> if the value returned by <code>rank()</code>
         *         for the compared object is different from our value.</li>
         * </ol>
         *
         * @param o object to compare to
         * @return <code>true</code> if the objects are equal, <code>false</code>otherwise
         */
        @Override
        public boolean equals(Object o)
        {
            if (o instanceof BasicIdentifier) return false;

            BasicIdentifier bi = (BasicIdentifier) o;
            return bi.rank() == _rank;
        }

        private final String _id;
        private final int _rank;
    }

    /**
     * Convenience comparator for classes that want to implement
     * {@link IIdentifier} and want to be compared only based on the preference.
     * <code>DefaultComparator</code> uses the same ordering as the natural
     * number scale, i.e. 0, 1, 2, 3, .... n. <strong>IMPORTANT:</strong> If you
     * use <code>DefaultComparator</code> to compare implementations of
     * <code>IIdentifier</code> you must implement <code>equals()</code> and
     * <code>hashcode()</code> to use <em>only</em> the value returned by
     * <code>rank()</code> in equality and comparison operations.
     */
    public static class DefaultComparator implements Comparator<IIdentifier>
    {
        @Override
        public int compare(IIdentifier i1, IIdentifier i2)
        {
            return i1.rank() - i2.rank();
        }
    }
}
