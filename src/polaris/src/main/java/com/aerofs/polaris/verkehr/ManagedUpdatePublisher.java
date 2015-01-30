package com.aerofs.polaris.verkehr;

import com.aerofs.baseline.Managed;
import com.aerofs.polaris.notification.UpdatePublisher;

/**
 * Convenience interface that combines both {@code Managed} and {@code UpdatePublisher}.
 * Simplifies the use of mocks in Polaris unit tests.
 */
public interface ManagedUpdatePublisher extends Managed, UpdatePublisher {

    // this space intentionally left empty
}
