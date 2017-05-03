/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.util;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

public class MimeTypeDetector extends DefaultDetector
{
    private static final long serialVersionUID = 0L;
    public final static String APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final Logger l = LoggerFactory.getLogger(MimeTypeDetector.class);

    public String detect(String name)
    {
        String type = APPLICATION_OCTET_STREAM;
        try {
            Metadata m = new Metadata();
            // NB: Tika "helpfully" detects and ignore URL query/fragment
            // This completely breaks detection for filenames that include question marks
            // as it results in the entire prefix being stripped. Work around this URL-encoding
            // the file name if needed.
            m.set(TikaMetadataKeys.RESOURCE_NAME_KEY,
                    name != null && name.indexOf('?') != -1 ? URLEncoder.encode(name, "UTF-8") : name);
            // NB: only use name-based detection for now
            try (InputStream s = new ByteArrayInputStream(new byte[0])) {
                type = detect(s, m).toString();
                l.info("mediatype detected: {} -> {}", name, type);
            }
        } catch (IOException e) {
            l.warn("mediatype detection failed", e);
        }
        return type;
    }
}
