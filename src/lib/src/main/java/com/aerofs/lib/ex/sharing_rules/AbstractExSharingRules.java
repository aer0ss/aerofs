/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.sharing_rules;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public abstract class AbstractExSharingRules extends AbstractExWirable
{
    private static final long serialVersionUID = 0L;

    private static final String SHARING_EXTERNAL_TITLE = "Sharing with external users";

    public static class DetailedDescription
    {
        // These description are used by both desktop and web
        // N.B. "{}" is a placeholder for a list of users (MUST be the only characters in a line)
        // NB: in the future this Type enum will be replaced by user-defined exceptions
        public static enum Type
        {
            WARNING_EXTERNAL_SHARING(SHARING_EXTERNAL_TITLE,
                    "You are about to share this folder with external users.\n\n"
                    + "Internal users will lose write access to it.\n\n"
                    + "Please ensure that this folder contains no confidential material before "
                    + "proceeding."
            ),
            WARNING_DOWNGRADE(SHARING_EXTERNAL_TITLE,
                    "This folder is shared with the following external users:\n"
                    + "\n{}\n\n"
                    + "To avoid accidental data leaks, internal Owners are downgraded to Managers, "
                    + "and internal Editors to Viewers."
            ),
            WARNING_NO_EXTERNAL_OWNERS(SHARING_EXTERNAL_TITLE,
                    "External Users cannot manage or own a shared folder.\n\n"
                    + "To avoid accidental data leaks, external Owners are downgraded to Editors."
            );

            private final String title;
            private final String description;

            Type(String title, String description)
            {
                this.title = title;
                this.description = description;
            }
        }

        public final Type type;
        public final String title;
        public final String description;
        public final Map<UserID, FullName> users;

        public DetailedDescription(Type type, ImmutableMap<UserID, FullName> users)
        {
            this.type = type;
            this.title = type.title;
            this.description = type.description;
            this.users = users;
        }

        @Override
        public String toString()
        {
            return type.toString();
        }
    }

    // In case you're wondering, Gson is thread-safe.
    private final static Gson _gson = new GsonBuilder()
            .registerTypeAdapter(UserID.class, new UserIDDeserializer())
            .create();

    protected final List<DetailedDescription> _decodedExceptionData;

    protected AbstractExSharingRules(List<DetailedDescription> decodedExceptionData)
    {
        super(BaseUtil.string2utf(_gson.toJson(decodedExceptionData)));
        _decodedExceptionData = decodedExceptionData;
    }

    protected AbstractExSharingRules(PBException pb)
    {
        super(pb);
        Type listType = new TypeToken<List<DetailedDescription>>() {}.getType();
        _decodedExceptionData = _gson.fromJson(pb.getData().toStringUtf8(), listType);
    }

    private static class UserIDDeserializer
            implements JsonDeserializer<UserID>, JsonSerializer<UserID>
    {
        @Override
        public UserID deserialize(JsonElement elem, Type type, JsonDeserializationContext cxt)
                throws JsonParseException
        {
            try {
                return UserID.fromExternal(elem.getAsJsonPrimitive().getAsString());
            } catch (ExInvalidID e) {
                throw new JsonParseException(e);
            }
        }

        @Override
        public JsonElement serialize(UserID userID, Type type, JsonSerializationContext cxt)
        {
            return new JsonPrimitive(userID.getString());
        }
    }
}
