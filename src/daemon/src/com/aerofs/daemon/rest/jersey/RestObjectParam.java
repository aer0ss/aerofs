package com.aerofs.daemon.rest.jersey;

import com.aerofs.daemon.rest.RestObject;

public class RestObjectParam extends AbstractParam<RestObject>
{
    public RestObjectParam(String input)
    {
        super(input);
    }

    @Override
    protected RestObject parse(String input) throws Exception
    {
        return RestObject.fromStringFormal(input);
    }
}
