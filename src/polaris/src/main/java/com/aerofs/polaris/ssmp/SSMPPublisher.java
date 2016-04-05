package com.aerofs.polaris.ssmp;

import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.notification.BinaryPublisher;
import com.aerofs.polaris.notification.UpdatePublisher;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPRequest;
import com.aerofs.ssmp.SSMPResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Arrays;

import static com.aerofs.polaris.api.PolarisUtilities.getUpdateTopic;
import static com.aerofs.ssmp.SSMPDecoder.MAX_PAYLOAD_LENGTH;

// FIXME (AG): this is a piss-poor updater. I have another approach in mind where you can dump out the actual transforms
@Singleton
public final class SSMPPublisher implements UpdatePublisher, BinaryPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSMPPublisher.class);
    private final SSMPConnection ssmp;

    @Inject
    public SSMPPublisher(ManagedSSMPConnection managedSSMPConnection) {
        LOGGER.info("setup lipwig update publisher");
        this.ssmp = managedSSMPConnection.conn;
    }

    @Override
    public ListenableFuture<Void> publishUpdate(String topic, Update update) {
        SettableFuture<Void> returned = SettableFuture.create();

        LOGGER.debug("notify {}", topic);

        try {
            Futures.addCallback(
                    ssmp.request(SSMPRequest.mcast(SSMPIdentifier.fromInternal(getUpdateTopic(topic)),
                            Long.toString(update.latestLogicalTimestamp))), ssmpRequestCallback(topic, returned));
        } catch (Exception e) {
            LOGGER.warn("fail publish notification for {}", topic);
            returned.setException(e);
        }

        return returned;
    }

    @Override
    public ListenableFuture<Void> publishBinary(String topic, byte[] payload, int elementSize) {
        SettableFuture<Void> returned = SettableFuture.create();

        LOGGER.debug("notify {}", topic);

        try {
            int chunkSize = (MAX_PAYLOAD_LENGTH) / elementSize * elementSize;
            if (payload.length < MAX_PAYLOAD_LENGTH) {
                Futures.addCallback(ssmp.request(
                        SSMPRequest.mcast(SSMPIdentifier.fromInternal(topic), payload)),
                        ssmpRequestCallback(topic, returned));
            } else {
                int index = 0;
                while (payload.length - index > MAX_PAYLOAD_LENGTH) {
                    Futures.addCallback(
                            ssmp.request(SSMPRequest.mcast(SSMPIdentifier.fromInternal(topic),
                                            Arrays.copyOfRange(payload, index, index + chunkSize))),
                            ssmpRequestCallback(topic, returned));
                }
                index += chunkSize;
            }
        } catch (Exception e) {
            LOGGER.warn("fail publish notification for {}", topic);
            returned.setException(e);
        }

        return returned;
    }

    private FutureCallback<SSMPResponse> ssmpRequestCallback(String topic,
            SettableFuture<Void> returned) {
        return new FutureCallback<SSMPResponse>() {
            @Override
            public void onSuccess(@Nullable SSMPResponse result) {
                LOGGER.debug("done notify {} {}", topic, result.code);
                if (result.code == SSMPResponse.OK || result.code == SSMPResponse.NOT_FOUND) {
                    returned.set(null);
                } else {
                    returned.setException(new Exception("failed notify " + topic + " " + result.code));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.debug("fail notify {}", topic);
                returned.setException(t);
            }
        };
    }
}
