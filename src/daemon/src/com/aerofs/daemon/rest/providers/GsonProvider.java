package com.aerofs.daemon.rest.providers;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonWriter;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GsonProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    private final Gson _gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .create();

    /**
     * Enforce ISO 8601 format and UTC timezone for date serialization
     */
    public static class DateTypeAdapter implements JsonSerializer<Date>
    {
        private final DateFormat dateFormat;

        private DateTypeAdapter() {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        @Override
        public synchronized JsonElement serialize(Date date, Type type,
                JsonSerializationContext jsonSerializationContext) {
            synchronized (dateFormat) {
                // DateFormat is not thread-safe
                String dateFormatAsString = dateFormat.format(date);
                return new JsonPrimitive(dateFormatAsString);
            }
        }
    }

    // READER

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return true;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException
    {
        return _gson.fromJson(new InputStreamReader(entityStream, Charsets.UTF_8), type);
    }

    // WRITER

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return true;
    }

    @Override
    public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return -1;
    }

    @Override
    public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException
    {
        JsonWriter jsw = new JsonWriter(new OutputStreamWriter(entityStream, Charsets.UTF_8));
        try {
            _gson.toJson(t, type, jsw);
        } finally {
            jsw.close();
        }
    }
}