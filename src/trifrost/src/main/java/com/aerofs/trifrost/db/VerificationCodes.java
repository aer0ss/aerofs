package com.aerofs.trifrost.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface VerificationCodes {
    @SqlUpdate(
            "INSERT INTO verification_codes (email, code, created) " +
                    "VALUES(:email, :code, :created)")
    int add(
            @Bind("email") String email,
            @Bind("code") String code,
            @Bind("created") long createdMs);

    @SqlUpdate(
            "DELETE FROM verification_codes " +
                    "WHERE email = :email AND code = :code")
    int consumeVerification(@Bind("email") String email, @Bind("code") String code);
}
