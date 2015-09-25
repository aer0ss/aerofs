package com.aerofs.trifrost.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import javax.annotation.Nullable;

/**
 */
public interface UserAddresses {
    @SqlUpdate(
            "INSERT INTO addresses (email, user_id) " +
                    "VALUES (:email, :user_id)")
    void add(@Bind("email") String email,
                    @Bind("user_id") String userId);

    @Nullable
    @SqlQuery(
            "SELECT user_id " +
                    "FROM addresses " +
                    "WHERE email = :email")
    String get(@Bind("email") String email);
}
