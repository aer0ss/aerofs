package com.aerofs.polaris.external_api.metadata;

import com.aerofs.polaris.api.operation.Updated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.List;


public final class ApiOperationResult
{
    @NotNull
    @Valid
    public final List<Updated> updated;

    @NotNull
    public final Response response;


    public ApiOperationResult(List<Updated> updated, Response response)
    {
        this.updated = updated;
        this.response = response;
    }
}
