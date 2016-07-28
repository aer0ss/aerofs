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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public abstract class Resources {

    public static final String DIGEST_ALGORITHM = "MD5";

    public static final String BASIC_RESOURCE = "basic";

    public static final String EMPTY_ENTITY_RESOURCE = "empty";

    public static final String PIPELINED_RESOURCE = "pipelined";

    public static final String CHUNKED_DOWNLOAD_RESOURCE = "download";

    public static final String CHUNKED_UPLOAD_RESOURCE = "upload";

    public static final String HANDS_ON_CHUNKED_UPLOAD_RESOURCE = "handson";

    public static final String POLLING_RESOURCE = "polling";

    public static final String THROWING_RESOURCE = "throwing";

    public static final String TIMEOUT_RESOURCE = "timeout";

    public static byte[] getRandomBytes(int length) {
        Random random = new Random();
        byte[] generated = new byte[length];
        random.nextBytes(generated);
        return generated;
    }

    public static String getHexDigest(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] digested = digest.digest(bytes);
        return BaseEncoding.base16().encode(digested);
    }

    public static String getHexDigest(byte[]... chunks) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        for (byte[] chunk : chunks) {
            digest.update(chunk);
        }

        byte[] digested = digest.digest();
        return BaseEncoding.base16().encode(digested);
    }

    private Resources() {
        // to prevent instantiation by subclasses
    }
}
