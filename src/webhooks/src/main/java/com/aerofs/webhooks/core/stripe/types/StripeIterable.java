package com.aerofs.webhooks.core.stripe.types;

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class StripeIterable<T extends AbstractStripeApiObject> implements Iterable<T> {

    @JsonProperty( DATA_KEY ) private final ImmutableList<T> data;
    @JsonProperty( URL_KEY ) private final String url;
    @JsonProperty( COUNT_KEY ) private final int count;
    @JsonProperty( AbstractStripeApiObject.TYPE_KEY ) private final String type;

    @JsonCreator
    private StripeIterable( @JsonProperty( AbstractStripeApiObject.TYPE_KEY ) final String type,
            @JsonProperty( DATA_KEY ) final List<T> data,
            @JsonProperty( URL_KEY ) final String url,
            @JsonProperty( COUNT_KEY ) final int count ) {
        this.data = ImmutableList.copyOf( data );
        this.url = url;
        this.count = count;
        this.type = STRIPE_ID;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .addValue( data )
                .toString();
    }

    @Override
    public Iterator<T> iterator() {
        return data.iterator();
    }

    private static final String DATA_KEY = "data";
    private static final String URL_KEY = "url";
    private static final String COUNT_KEY = "count";

    private static final String STRIPE_ID = "list";

}
