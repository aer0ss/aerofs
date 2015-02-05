/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.ContentHash;
import com.google.common.io.BaseEncoding;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;

public class GsonUtil
{
    private final static BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    public final static Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(UniqueID.class, (JsonDeserializer<UniqueID>)(elem, type, cxt) -> {
                try {
                    return UniqueID.fromStringFormal(elem.getAsString());
                } catch (Exception e) {
                    throw new JsonParseException(e);
                }
            })
            .registerTypeAdapter(OID.class, (JsonDeserializer<OID>)(elem, type, cxt) -> {
                try {
                    return new OID(elem.getAsString());
                } catch (Exception e) {
                    throw new JsonParseException(e);
                }
            })
            .registerTypeAdapter(ContentHash.class, (JsonDeserializer<ContentHash>)(elem, type, cxt) -> {
                try {
                    return new ContentHash(HEX.decode(elem.getAsString()));
                } catch (Exception e) {
                    throw new JsonParseException(e);
                }
            })
            .create();

}
