package com.aerofs.sp.server;

import com.aerofs.ssmp.SSMPResponse;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class LipwigUtil {

    /**
     * Utility to minimize duped code in the below lipwig-related methods.
     */
    static void lipwigFutureGet(ListenableFuture<SSMPResponse> future)
            throws Exception
    {
        try {
            SSMPResponse r = future.get();
            if (r.code == SSMPResponse.NOT_FOUND) {
                // NB: UCAST will 404 if the user is not connected
                // NB: MCAST will 404 if no user subscribed to the topic
            } else if (r.code != SSMPResponse.OK) {
                throw new Exception("unexpected response " + r.code);
            }
        } catch (InterruptedException e) {
            throw new Error("publisher client should never be interrupted");
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) {
                throw (Exception) e.getCause();
            } else {
                throw new Error("cannot handle arbitrary throwable");
            }
        }
    }
}
