package com.aerofs.rest.auth;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpRequestContext;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DelegatedUserDeviceExtractor implements AuthTokenExtractor<IUserAuthToken>
{
    private final static Pattern DELEGATED_PATTERN =
            Pattern.compile("Aero-Delegated-User-Device ([a-zA-Z0-9\\-_]+) ([0-9a-f]{32}) ([a-zA-Z0-9+/]+=*) ([0-9a-f]{32})");

    private final static Base64.Decoder base64 = Base64.getDecoder();

    private final String secret;

    public DelegatedUserDeviceExtractor(String secret)
    {
        this.secret = secret;
    }

    @Override
    public String challenge()
    {
        return "Aero-Delegated-User-Device realm=\"AeroFS\"";
    }

    @Nullable
    @Override
    public IUserAuthToken extract(HttpContext context)
    {
        HttpRequestContext req = context.getRequest();

        List<String> headers = req.getRequestHeader(HttpHeaders.Names.AUTHORIZATION);
        if (headers == null || headers.size() != 1) return null;

        Matcher m = DELEGATED_PATTERN.matcher(headers.get(0));

        if (!m.matches()) return null;

        if (!BaseSecUtil.constantTimeIsEqual(secret, m.group(2))) {
            throw AuthTokenExtractor.unauthorized("invalid secret", challenge());
        }

        try {
            return new DelegatedUserDeviceToken(m.group(1),
                    UserID.fromExternal(new String(base64.decode(m.group(3)), StandardCharsets.UTF_8)),
                    UniqueID.fromStringFormal(m.group(4)));
        } catch (ExEmptyEmailAddress| UniqueID.ExInvalidID e) {
            throw AuthTokenExtractor.unauthorized("could not verify purported identity", challenge());
        }
    }
}
