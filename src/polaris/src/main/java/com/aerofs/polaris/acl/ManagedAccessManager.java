package com.aerofs.polaris.acl;

import com.aerofs.baseline.Managed;

/**
 * Convenience interface that combines both {@code Managed} and {@code AccessManager}.
 * Simplifies the use of mocks in Polaris unit tests.
 */
public interface ManagedAccessManager extends Managed, AccessManager {

    // this space intentionally left empty
}
