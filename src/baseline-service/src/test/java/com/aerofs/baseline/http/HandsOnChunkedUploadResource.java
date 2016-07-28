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

import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Singleton
@Path("/" + Resources.HANDS_ON_CHUNKED_UPLOAD_RESOURCE)
public final class HandsOnChunkedUploadResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandsOnChunkedUploadResource.class);

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public String uploadBytes(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");

        int total = 0;
        int read;
        byte[] chunk = new byte[1024];
        while ((read = inputStream.read(chunk)) != -1) {
            digest.update(chunk, 0, read);
            total += read;
        }

        byte[] digested = digest.digest();
        String hex = BaseEncoding.base16().encode(digested);
        LOGGER.info("chunked length:{} digest:{}", total, hex);
        return hex;
    }
}
