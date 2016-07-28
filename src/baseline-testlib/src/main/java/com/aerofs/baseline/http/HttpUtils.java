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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public abstract class HttpUtils {

    public static String readResponseEntityToString(HttpResponse response) throws IOException {
        Preconditions.checkArgument(response.getEntity().getContentLength() > 0,  "entity must have non-zero content length");
        return readStreamToString(response.getEntity().getContent());
    }

    // see http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
    public static String readStreamToString(InputStream in) throws IOException {
        Scanner scanner = new Scanner(in).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    public static byte[] readStreamToBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            int bytesRead;
            byte[] chunk = new byte[1024];

            while ((bytesRead = in.read(chunk)) != -1) {
                out.write(chunk, 0, bytesRead);
            }

            return out.toByteArray();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                // noop
            }
        }
    }

    public static BasicHttpEntity writeStringToEntity(String content) {
        BasicHttpEntity basic = new BasicHttpEntity();
        basic.setContent(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8)));
        return basic;
    }

    private HttpUtils() {
        // to prevent instantiation by subclasses
    }
}
