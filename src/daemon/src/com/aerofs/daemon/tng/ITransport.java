/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;

import java.util.Comparator;

/**
 * A Transport Layer Manifesto (with apologies to Dr. Martin Luther King, Jr.)
 *
 * In a sense we have come to our transport layer to cash a check.
 * When the architects of our system wrote the magnificent code,
 * they were signing a promissory note to which every packet was to fall heir.
 * This note was a promise that all data, big and small, would be guaranteed
 * the unalienable rights of availability, partition tolerance, and pursuit of consistency.
 *
 * I have a dream,
 * that one day all transports will be treated equally;
 * that packets will not be judged by their size
 * but by the content of their data.
 *
 * Now is the time to make real the promises of an agnostic transport.
 * Now is the time to refactor from the dark and desolate valley of segregated transports to the sunlit path of clear API.
 * Now is the time to lift our codebase from the quicksands of implied contract to the solid rock of external consistency.
 * Now is the time to make our stuff work cleanly.
 *
 * No, no, we are not satisfied,
 * and we will not be satisfied
 * until datagrams flow down like waters
 * and messages like a mighty stream.
 */
public interface ITransport extends IUnicast, IMaxcast, IStartable, IDebug
{
    String id();

    Preference pref();

    public static final class PreferenceBasedTransportComparator implements Comparator<ITransport>
    {
        @Override
        public int compare(ITransport t1, ITransport t2)
        {
            return t1.pref().pref() - t2.pref().pref();
        }
    }

    public static final Comparator<ITransport> DEFAULT_COMPARATOR = new PreferenceBasedTransportComparator();
}
