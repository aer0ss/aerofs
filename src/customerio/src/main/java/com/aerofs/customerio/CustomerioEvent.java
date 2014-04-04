package com.aerofs.customerio;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
public class CustomerioEvent<T> {

    @JsonProperty( NAME_KEY ) private final String name;
    @JsonProperty( DATA_KEY ) private final Optional<T> data;

    public CustomerioEvent( final String name, final Optional<T> data ) {
        checkArgument( isNotBlank( name ), "name cannot be blank" );
        this.name = name;

        this.data = checkNotNull( data, "data cannot be null, use Optional.absent() instead" );
    }

    public String getName() {
        return name;
    }

    public Optional<T> getData() {
        return data;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .add( NAME_KEY, name )
                .add( DATA_KEY, data )
                .toString();
    }

    private static final String NAME_KEY = "name";
    private static final String DATA_KEY = "data";

}
