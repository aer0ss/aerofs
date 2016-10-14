package com.aerofs.restless.providers;


import com.aerofs.restless.stream.ContentStream;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Dummy message body writer for {@link ContentStream} entity
 *
 * The actual writing is done by {@link com.aerofs.restless.netty.ChunkedContentStream}
 */
@Provider
@Produces(MediaType.WILDCARD)  // handle all ContentStream entities regardless of Content-Type
public class ContentStreamProvider implements MessageBodyWriter<ContentStream>
{
    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations,
            MediaType mediaType)
    {
        return ContentStream.class.isAssignableFrom(aClass);
    }

    @Override
    public long getSize(ContentStream contentStream, Class<?> aClass, Type type,
            Annotation[] annotations, MediaType mediaType)
    {
        // size not known
        return -1;
    }

    @Override
    public void writeTo(ContentStream contentStream, Class<?> aClass, Type type,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream)
            throws IOException, WebApplicationException
    {
        // noop
    }
}
