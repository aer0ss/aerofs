package com.aerofs.polaris.sparta;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// users of this class currently do not care about these fields of the API response, so we ignore deserializing them
@JsonIgnoreProperties({"members", "groups", "pending", "is_external", "caller_effective_permissions", "is_locked"})
public class SharedFolder {
    public final UniqueID id;
    public final String name;

    @JsonCreator
    public SharedFolder(@JsonProperty("id")UniqueID id, @JsonProperty("name") String name )
    {
        this.id = id;
        this.name = name;
    }
}
