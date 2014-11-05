/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris;


import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;

public class GsonUtil
{
    public final static Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(UniqueID.class, (JsonDeserializer<UniqueID>)(elem, type, cxt) -> {
                try {
                    return UniqueID.fromStringFormal(elem.getAsString());
                } catch (ExFormatError e) {
                    throw new JsonParseException(e);
                }
            })
            .registerTypeAdapter(OID.class, (JsonDeserializer<OID>)(elem, type, cxt) -> {
                try {
                    return new OID(elem.getAsString());
                } catch (ExFormatError e) {
                    throw new JsonParseException(e);
                }
            })
            .create();

}