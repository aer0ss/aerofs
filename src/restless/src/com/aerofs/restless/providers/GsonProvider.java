package com.aerofs.restless.providers;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.*;
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
    private final static Logger l = LoggerFactory.getLogger(GsonProvider.class);

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    /**
     * Enforce ISO 8601 format and UTC timezone for date serialization
     */
    public static class DateTypeAdapter implements JsonSerializer<Date>
    {
        // DateFormat is not thread-safe
        private final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<>();

        @Override
        public JsonElement serialize(Date date, Type type,
                JsonSerializationContext jsonSerializationContext) {
            DateFormat fmt = dateFormat.get();
            if (fmt == null) {
                fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                dateFormat.set(fmt);
            }
            String dateFormatAsString = fmt.format(date);
            return new JsonPrimitive(dateFormatAsString);
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
        return GSON.fromJson(new InputStreamReader(entityStream, Charsets.UTF_8), type);
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
            throws IOException
    {
        try (JsonWriter jsw = new JsonWriter(new OutputStreamWriter(entityStream, Charsets.UTF_8))) {
            GSON.toJson(t, type, jsw);
        } catch (Throwable e) {
            l.warn("serialization failed", e);
            Throwables.propagateIfInstanceOf(e, IOException.class);
            throw Throwables.propagate(e);
        }
    }
}