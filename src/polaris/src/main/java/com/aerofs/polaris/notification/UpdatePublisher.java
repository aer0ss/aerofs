package com.aerofs.polaris.notification;

import com.aerofs.polaris.api.notification.Update;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Implemented by classes that publish updates from polaris.
 * <br>
 * Note that <em>when</em> polaris makes updates is irrelevant.
 * <br>
 * Implementations <strong>MUST</strong> be thread-safe.
 */
public interface UpdatePublisher {

    /**
     * Publish an update.
     * <br>
     * Publication is <strong>best-effort only</strong>. If the
     * implementation cannot publish the update it notifies the sender
     * via the returned future, but may continue to
     * publish subsequent updates.
     *
     * @param topic topic to which the update is published
     * @param update update message to be published
     * @return future representing the publication state
     */
    ListenableFuture<Void> publishUpdate(String topic, Update update);
}
