package com.aerofs.webhooks.core.stripe.types;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

public abstract class AbstractIdentifiedStripeApiObject extends AbstractStripeApiObject {

    @JsonProperty( ID_KEY ) private final String id;

    protected AbstractIdentifiedStripeApiObject( final String id, final String type ) {
        super( type );
        this.id = checkNotNull( id, "id cannot be null" );
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( TYPE_KEY, getType() )
                .add( ID_KEY, getId() )
                .toString();
    }

    public String getId() {
        return id;
    }

    protected static final String ID_KEY = "id";

}
