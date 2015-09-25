package com.aerofs.trifrost.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface AuthTokens {

    @GetGeneratedKeys
    @SqlUpdate("INSERT INTO auth_tokens (token, refresh_token, expiry) VALUES (:auth_token, :refresh_token, :expiry)")
    public int add(@Bind("auth_token") String authToken,
                   @Bind("refresh_token") String refreshToken,
                   @Bind("expiry") long expiry);

    @SqlQuery(
            "SELECT COUNT(*) FROM auth_tokens AS a, refresh_tokens as r " +
            "WHERE " +
                "a.token = :auth_token AND " +
                "a.refresh_token = r.token AND " +
                "r.user_id = :user_id AND " +
                "a.expiry >= UNIX_TIMESTAMP() * 1000")
    public int isValidForUser(@Bind("auth_token") String authToken,
                              @Bind("user_id") String userId);
}
