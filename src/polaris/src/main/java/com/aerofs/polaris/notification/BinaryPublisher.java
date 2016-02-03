package com.aerofs.polaris.notification;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Implemented by classes that publish updates from polaris.
 * <br>
 * Note that <em>when</em> polaris makes updates is irrelevant.
 * <br>
 * Implementations <strong>MUST</strong> be thread-safe.
 */
public interface BinaryPublisher {

    /**
     * Publish a binary SSMP notification.
     * <br>
     * Publication is <strong>best-effort only</strong>. If the
     * implementation cannot publish the update it notifies the sender
     * via the returned future, but may continue to
     * publish subsequent updates.
     *
     * @param topic topic to which the update is published
     * @param payload payload to be published
     * @param chunkSize the size of chunks that the payload may be broken into if it's too large for SSMP
     * @return future representing the publication state
     */
    ListenableFuture<Void> publishBinary(String topic, byte[] payload, int chunkSize);
}
