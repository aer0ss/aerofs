package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.Charset;
import java.util.Map;

public abstract class AbstractExSharedFolderRules extends AbstractExWirable
{
    // this is package private because it is needed in unit tests
    protected static final String SERIALIZATION_ENCODING = "UTF-8";

    private static final long serialVersionUID = 0;

    private ImmutableMap<UserID, FullName> _externalUsers;

    protected AbstractExSharedFolderRules(ImmutableMap<UserID, FullName> externalUsers)
    {
        super(serializeUserIDFullNameMap(externalUsers));

        _externalUsers = externalUsers;
    }

    protected AbstractExSharedFolderRules(PBException pb)
    {
        super(pb);

        byte[] data = getDataNullable();

        if (data != null) _externalUsers = deserializeUserIDFullNameMap(data);
    }

    public ImmutableMap<UserID, FullName> getExternalUsers()
    {
        return _externalUsers;
    }

    private static byte[] serializeUserIDFullNameMap(ImmutableMap<UserID, FullName> map)
    {
        // N.B. this impl. works for all objects, but we really only need it for a specific type.
        return new Gson().toJson(map).getBytes(Charset.forName(SERIALIZATION_ENCODING));
    }

    private static ImmutableMap<UserID, FullName> deserializeUserIDFullNameMap(byte[] data)
    {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(UserID.class, new UserIDDeserializer())
                .create();

        String jsonString = new String(data, Charset.forName(SERIALIZATION_ENCODING));

        // this is a hideous workaround to deal with Java type erasure.
        // see https://sites.google.com/site/gson/gson-user-guide
        java.lang.reflect.Type type = new TypeToken<Map<UserID, FullName>>(){}.getType();

        Map<UserID, FullName> map = gson.fromJson(jsonString, type);

        return ImmutableMap.copyOf(map);
    }

    private static class UserIDDeserializer implements JsonDeserializer<UserID>
    {
        @Override
        public UserID deserialize(JsonElement jsonElement, java.lang.reflect.Type type,
                JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException
        {
            try {
                return UserID.fromExternal(jsonElement.getAsJsonPrimitive().getAsString());
            } catch (ExEmptyEmailAddress e) {
                throw new JsonParseException(e);
            }
        }
    }
}
