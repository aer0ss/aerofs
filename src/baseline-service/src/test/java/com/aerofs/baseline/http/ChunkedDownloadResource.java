/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.http;

import com.google.common.io.Closeables;
import org.glassfish.jersey.server.ChunkedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

@Singleton
@Path("/" + Resources.CHUNKED_DOWNLOAD_RESOURCE)
public final class ChunkedDownloadResource {

    public static final byte[][] RESOURCE_BYTES = new byte[][] {
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
            Resources.getRandomBytes(1024),
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkedDownloadResource.class);

    // based on example in https://jersey.java.net/documentation/latest/async.html
    @GET
    public ChunkedOutput<byte[]> getChunked() throws NoSuchAlgorithmException {
        String digested = Resources.getHexDigest(ChunkedDownloadResource.RESOURCE_BYTES);
        LOGGER.info("chunked digest:{}", digested);

        final ChunkedOutput<byte[]> output = new ChunkedOutput<>(byte[].class);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < RESOURCE_BYTES.length; i++) {
                        output.write(RESOURCE_BYTES[i]);
                        LOGGER.info("write chunk {}", i);
                        Thread.sleep(1000);
                    }
                } catch (IOException e) {
                    LOGGER.warn("fail chunked output write", e);
                } catch (InterruptedException e) {
                    LOGGER.warn("interrupted during chunked output write");
                } finally {
                    try {
                        Closeables.close(output, true);
                    } catch (IOException e) {
                        LOGGER.warn("fail close chunked output");
                    }
                }
            }
        }).start();

        return output;
    }
}
