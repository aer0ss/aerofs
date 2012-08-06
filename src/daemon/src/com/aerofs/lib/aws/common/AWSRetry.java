package com.aerofs.lib.aws.common;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;

import com.aerofs.lib.Util;

public class AWSRetry
{
    private static final Logger l = Util.l(AWSRetry.class);

    private static final int EXP_BACKOFF_COEFFICIENT = 2;
    private static final int MIN_WAIT_TIME = 100;
    private static final int MAX_WAIT_TIME = 60000;
    private final static int SERVER_ERROR_START_RANGE = 500;
    private final static int SERVER_ERROR_END_RANGE = 599;

    private long _interval = MIN_WAIT_TIME;

    public long delayMillisBeforeRetry(AmazonServiceException e) throws AmazonServiceException
    {
        if (!isServerError(e)) throw e;
        long delay = _interval;
        _interval = Math.min(_interval * EXP_BACKOFF_COEFFICIENT, MAX_WAIT_TIME);
        return delay;
    }

    public static boolean isServerError(AmazonServiceException e)
    {
        return e.getStatusCode() >= SERVER_ERROR_START_RANGE &&
                e.getStatusCode() <= SERVER_ERROR_END_RANGE;
    }

    public static <T> T retry(Callable<T> callable) throws IOException
    {
        AWSRetry retry = new AWSRetry();
        while (true) {
            try {
                return callable.call();
            } catch (AmazonServiceException e) {
                try {
                    long delay = retry.delayMillisBeforeRetry(e);
                    l.warn("retry in " + delay + ": " + e);
                    Util.sleepUninterruptable(delay);
                } catch (AmazonServiceException e2) {
                    l.warn(e2);
                    throw new IOException(e2);
                }
            } catch (AmazonClientException e) {
                Util.fatal(e);
            } catch (IOException e) {
                l.warn(e);
                throw e;
            } catch (Exception e) {
                l.warn(e);
                throw new IOException(e);
            }

        }
    }
}