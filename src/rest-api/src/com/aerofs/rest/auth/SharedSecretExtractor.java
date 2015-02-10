package com.aerofs.rest.auth;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpRequestContext;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SharedSecretExtractor implements AuthTokenExtractor<ServiceToken>
{
    private final static Pattern SHARED_SECRET_PATTERN =
            Pattern.compile("Aero-Service-Shared-Secret ([a-zA-Z0-9\\-_]+) ([0-9a-f]{32})");

    private final String secret;

    public SharedSecretExtractor(String secret)
    {
        this.secret = secret;
    }

    @Override
    public String challenge()
    {
        return "Aero-Service-Shared-Secret realm=\"AeroFS\"";
    }

    @Nullable
    @Override
    public ServiceToken extract(HttpContext context)
    {
        HttpRequestContext req = context.getRequest();

        List<String> headers = req.getRequestHeader(Names.AUTHORIZATION);
        if (headers == null || headers.size() != 1) return null;

        Matcher m = SHARED_SECRET_PATTERN.matcher(headers.get(0));

        if (!m.matches()) return null;

        if (!secret.equals(m.group(2))) {
            throw AuthTokenExtractor.unauthorized("invalid secret", challenge());
        }

        return new ServiceToken(m.group(1));
    }
}
