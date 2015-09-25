package com.aerofs.trifrost.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface RefreshTokens {

    @SqlUpdate("INSERT INTO refresh_tokens (token, user_id, expiry) VALUES (:refresh_token, :user_id, :expiry)")
    int add(@Bind("refresh_token") String refreshToken,
            @Bind("user_id") String userId,
            @Bind("expiry") long expiry);

    @SqlUpdate("DELETE FROM refresh_tokens WHERE token=:refresh_token AND user_id = :user_id")
    int invalidate(@Bind("refresh_token") String refreshToken,
                   @Bind("user_id") String userId);
}
