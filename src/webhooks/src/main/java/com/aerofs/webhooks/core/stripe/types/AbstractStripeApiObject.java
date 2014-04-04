package com.aerofs.webhooks.core.stripe.types;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

public abstract class AbstractStripeApiObject implements StripeApiObject {

    @JsonProperty( TYPE_KEY ) private final String type;
    
    protected AbstractStripeApiObject( final String type ) {
        checkArgument( isNotBlank( type ), "type cannot be blank" );
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( TYPE_KEY, getType() )
                .toString();
    }

    protected static final String CREATED_KEY = "created";
    protected static final String LIVEMODE_KEY = "livemode";

}
