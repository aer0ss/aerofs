package com.aerofs.polaris.api;

import com.aerofs.ids.*;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.OperationDeserializer;
import com.aerofs.polaris.api.types.AeroTypes;
import com.aerofs.polaris.deserializer.FileDeserializer;
import com.aerofs.polaris.deserializer.FolderDeserializer;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;

import java.text.SimpleDateFormat;

public final class PolarisModule extends Module {

    @Override
    public String getModuleName() {
        return "polaris";
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, "NONE", "com.aerofs.polaris", "polaris-api");
    }

    @Override
    public void setupModule(SetupContext context) {
        context.setNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(new AeroTypes.Base16Serializer());
        serializers.addSerializer(new AeroTypes.UTF8StringSerializer());
        serializers.addSerializer(new AeroTypes.UniqueIDSerializer());
        serializers.addSerializer(new AeroTypes.OIDSerializer());
        serializers.addSerializer(new AeroTypes.SIDSerializer());
        serializers.addSerializer(new AeroTypes.DIDSerializer());
        serializers.addSerializer(new AeroTypes.UserIDSerializer());
        serializers.addSerializer(new DateSerializer(false,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")));
        context.addSerializers(serializers);


        SimpleDeserializers deserializers = new SimpleDeserializers();
        deserializers.addDeserializer(byte[].class, new AeroTypes.Base16Deserializer());
        deserializers.addDeserializer(byte[].class, new AeroTypes.UTF8StringDeserializer());
        deserializers.addDeserializer(UniqueID.class, new AeroTypes.UniqueIDDeserializer());
        deserializers.addDeserializer(OID.class, new AeroTypes.OIDDeserializer());
        deserializers.addDeserializer(SID.class, new AeroTypes.SIDDeserializer());
        deserializers.addDeserializer(DID.class, new AeroTypes.DIDDeserializer());
        deserializers.addDeserializer(UserID.class, new AeroTypes.UserIDDeserializer());
        deserializers.addDeserializer(Operation.class, new OperationDeserializer());
        deserializers.addDeserializer(Folder.class, new FolderDeserializer());
        deserializers.addDeserializer(File.class, new FileDeserializer());
        context.addDeserializers(deserializers);
    }
}
