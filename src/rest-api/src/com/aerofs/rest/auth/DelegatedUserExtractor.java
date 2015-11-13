package com.aerofs.rest.auth;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpRequestContext;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DelegatedUserExtractor implements AuthTokenExtractor<IAuthToken> {
    private static final Pattern DELEGATED_PATTERN =
            Pattern.compile("Aero-Delegated-User-Device ([a-zA-Z0-9\\-_]+) ([0-9a-f]{32}) ([a-zA-Z0-9+/]+=*)");
    private static final Base64.Decoder base64 = Base64.getDecoder();
    private final String secret;

    public DelegatedUserExtractor(String secret)
    {
        this.secret = secret;
    }

    @Override
    public String challenge()
    {
        return "Aero-Delegated-User realm=\"AeroFS\"";
    }

    @Nullable
    @Override
    public IAuthToken extract(HttpContext context)
    {
        HttpRequestContext req = context.getRequest();
        List<String> headers = req.getRequestHeader(HttpHeaders.Names.AUTHORIZATION);
        if (headers == null || headers.size() != 1) return null;

        Matcher m = DELEGATED_PATTERN.matcher(headers.get(0));
        if (!m.matches() || !BaseSecUtil.constantTimeIsEqual(secret, m.group(2))) {
            throw AuthTokenExtractor.unauthorized("invalid secret", challenge());
        }

        try {
            return new DelegatedUserToken(m.group(1),
                    UserID.fromExternal(new String(base64.decode(m.group(3)), StandardCharsets.UTF_8)));
        } catch (ExInvalidID exInvalidID) {
            throw AuthTokenExtractor.unauthorized("could not verify purported identity", challenge());
        }
    }
}
