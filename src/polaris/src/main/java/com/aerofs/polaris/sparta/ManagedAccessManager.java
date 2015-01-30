package com.aerofs.polaris.sparta;

import com.aerofs.baseline.Managed;
import com.aerofs.polaris.acl.AccessManager;

/**
 * Convenience interface that combines both {@code Managed} and {@code AccessManager}.
 * Simplifies the use of mocks in Polaris unit tests.
 */
public interface ManagedAccessManager extends Managed, AccessManager {

    // this space intentionally left empty
}
