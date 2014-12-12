package com.aerofs.baseline;

import com.google.common.base.Charsets;
import org.apache.http.entity.BasicHttpEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public abstract class HttpUtils {

    // see http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
    public static String readStreamToString(InputStream in) {
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