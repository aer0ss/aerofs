/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.Loggers;
import com.google.inject.Inject;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.slf4j.Logger;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MimeTypeDetector extends DefaultDetector
{
    private static final long serialVersionUID = 0L;
    
    private final Logger l = Loggers.getLogger(MimeTypeDetector.class);

    @Inject
    public MimeTypeDetector()
    {

    }

    public String detect(String name)
    {
        String type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            Metadata m = new Metadata();
            m.set(TikaMetadataKeys.RESOURCE_NAME_KEY, name);
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
