package com.aerofs.polaris.notification;

import com.aerofs.baseline.Managed;

/**
 * Convenience interface that combines both {@code Managed} and {@code UpdatePublisher}.
 * Simplifies the use of mocks in Polaris unit tests.
 */
public interface ManagedUpdatePublisher extends Managed, UpdatePublisher {

    // this space intentionally left empty
}
