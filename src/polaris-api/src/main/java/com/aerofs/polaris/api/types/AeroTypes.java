package com.aerofs.polaris.api.types;

import com.aerofs.ids.*;
import com.aerofs.polaris.api.PolarisUtilities;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public abstract class AeroTypes {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeroTypes.class);

    private interface TypeCreator<T extends UniqueID> {

        T create(JsonParser parser) throws Exception;
    }

    //
    // UniqueID
    //

    public static final class UniqueIDSerializer extends StdSerializer<UniqueID> {

        public UniqueIDSerializer() {
            super(UniqueID.class);
        }

        @Override
        public void serialize(UniqueID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            writeSerialized(value, generator);
        }
    }

    public static final class UniqueIDDeserializer extends StdDeserializer<UniqueID> {

        private static final long serialVersionUID = 3753470406164917250L;

        public UniqueIDDeserializer() {
            super(UniqueID.class);
        }

        @Override
        public UniqueID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return AeroTypes.deserialize(parser, context, UniqueID.class, p -> new UniqueID(p.getText().trim()));
        }
    }

    //
    // OID
    //

    public static final class OIDSerializer extends StdSerializer<OID> {

        public OIDSerializer() {
            super(OID.class);
        }

        @Override
        public void serialize(OID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            writeSerialized(value, generator);
        }
    }

    public static final class OIDDeserializer extends StdDeserializer<OID> {

        private static final long serialVersionUID = -2018877680784383623L;

        public OIDDeserializer() {
            super(OID.class);
        }

        @Override
        public OID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return AeroTypes.deserialize(parser, context, OID.class, p -> new OID(p.getText().trim()));
        }
    }

    //
    // SID
    //

    public static final class SIDSerializer extends StdSerializer<SID> {

        public SIDSerializer() {
            super(SID.class);
        }

        @Override
        public void serialize(SID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            writeSerialized(value, generator);
        }
    }

    public static final class SIDDeserializer extends StdDeserializer<SID> {

        private static final long serialVersionUID = 7678933474056996468L;

        public SIDDeserializer() {
            super(SID.class);
        }

        @Override
        public SID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return AeroTypes.deserialize(parser, context, SID.class, p -> new SID(p.getText().trim()));
        }
    }

    //
    // DID
    //

    public static final class DIDSerializer extends StdSerializer<DID> {

        public DIDSerializer() {
            super(DID.class);
        }

        @Override
        public void serialize(DID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            writeSerialized(value, generator);
        }
    }

    public static final class DIDDeserializer extends StdDeserializer<DID> {

        private static final long serialVersionUID = -255788066681538191L;

        public DIDDeserializer() {
            super(DID.class);
        }

        @Override
        public DID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return AeroTypes.deserialize(parser, context, DID.class, p -> new DID(p.getText().trim()));
        }
    }

    public static final class DIDKeySerializer extends StdSerializer<DID> {

        public DIDKeySerializer() {
            super(DID.class);
        }

        @Override
        public void serialize(DID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeFieldName(value.toStringFormal());
        }
    }

    public static final class DIDKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            try {
                return new DID(key);
            } catch (ExInvalidID exInvalidID) {
                throw new IOException("invalid DID as key", exInvalidID);
            }
        }

    }



    //
    // UserID
    //

    public static final class UserIDSerializer extends StdSerializer<UserID> {

        public UserIDSerializer() {
            super(UserID.class);
        }

        @Override
        public void serialize(UserID value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.getString());
        }
    }

    public static final class UserIDDeserializer extends StdDeserializer<UserID> {

        private static final long serialVersionUID = -2816903074255619190L;

        public UserIDDeserializer() {
            super(UserID.class);
        }

        @Override
        public UserID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken current = parser.getCurrentToken();
            try {
                if (current.equals(JsonToken.VALUE_NULL)) {
                    return null;
                } else if (current.equals(JsonToken.VALUE_STRING)) {
                    return UserID.fromExternal(parser.getText().trim());
                } else {
                    throw context.mappingException(UserID.class, current);
                }
            } catch (Exception e) {
                LOGGER.warn("fail deserialize UserID", e);
                throw context.mappingException(UserID.class, current);
            }
        }
    }

    //
    // Hex Encoding
    //

    public static final class Base16Serializer extends StdSerializer<byte[]> {

        public Base16Serializer() {
            super(byte[].class);
        }

        @Override
        public void serialize(byte[] value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(PolarisUtilities.hexEncode(value));
        }
    }

    public static final class Base16Deserializer extends StdDeserializer<byte[]> {

        private static final long serialVersionUID = -8006607217301412098L;

        public Base16Deserializer() {
            super(byte[].class);
        }

        @Override
        public byte[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken current = parser.getCurrentToken();
            try {
                if (current.equals(JsonToken.VALUE_NULL)) {
                    return null;
                } else if (current.equals(JsonToken.VALUE_STRING)) {
                    return PolarisUtilities.hexDecode(parser.getText().toLowerCase().trim());
                } else {
                    throw context.mappingException(byte[].class, current);
                }
            } catch (Exception e) {
                LOGGER.warn("fail deserialize hex string", e);
                throw context.mappingException(byte[].class, current);
            }
        }
    }

    //
    // UTF8 String
    //

    public static final class UTF8StringSerializer extends StdSerializer<byte[]> {

        public UTF8StringSerializer() {
            super(byte[].class);
        }

        @Override
        public void serialize(byte[] value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(PolarisUtilities.stringFromUTF8Bytes(value));
        }
    }

    public static final class UTF8StringDeserializer extends StdDeserializer<byte[]> {

        private static final long serialVersionUID = -6578869100952605900L;

        public UTF8StringDeserializer() {
            super(byte[].class);
        }

        @Override
        public byte[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken current = parser.getCurrentToken();
            try {
                if (current.equals(JsonToken.VALUE_NULL)) {
                    return null;
                } else if (current.equals(JsonToken.VALUE_STRING)) {
                    return PolarisUtilities.stringToUTF8Bytes(parser.getText());
                } else {
                    throw context.mappingException(byte[].class, current);
                }
            } catch (Exception e) {
                LOGGER.warn("fail deserialize UTF-8 string", e);
                throw context.mappingException(byte[].class, current);
            }
        }
    }

    //
    // utility methods
    //

    private static void writeSerialized(UniqueID id, JsonGenerator generator) throws IOException {
        generator.writeString(id.toStringFormal());
    }

    @Nullable
    private static <T extends UniqueID> T deserialize(JsonParser parser, DeserializationContext context, Class<T> type, TypeCreator<T> creator) throws IOException {
        JsonToken current = parser.getCurrentToken();
        try {
            if (current.equals(JsonToken.VALUE_NULL)) {
                return null;
            } else if (current.equals(JsonToken.VALUE_STRING)) {
                return creator.create(parser);
            } else {
                throw context.mappingException(type, current);
            }
        } catch (Exception e) {
            LOGGER.warn("fail deserialize {}", type.getSimpleName(), e);
            throw context.mappingException(type, current);
        }
    }
}
