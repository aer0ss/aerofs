package com.aerofs.customerio;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
public class CustomerioCustomer {

    @JsonProperty( EMAIL_KEY ) private final String email;
    private final Optional<Map<String, Object>> attribtues;

    public CustomerioCustomer( final String email, final Optional<Map<String, Object>> attributes ) {
        checkArgument( isNotBlank( email ), "email cannot be blank" );
        this.email = email;

        this.attribtues = checkNotNull( attributes, "attributes cannot be null, use Optional.absent() instead" );
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( EMAIL_KEY, email )
                .add( "attributes", attribtues )
                .toString();
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return attribtues.orNull();
    }

    private static final String EMAIL_KEY = "email";

}
