package com.aerofs.trifrost.db;

import com.aerofs.trifrost.base.UniqueID;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import javax.annotation.Nullable;

public class Users {

    // Create a new user id; update the users and addresses tables
    public static String allocate(Handle conn, String email) {
        String uid = new String(UniqueID.generateUUID());
        conn.attach(IdInterface.class).alloc(uid);
        conn.attach(UserAddresses.class).add(email, uid);
        return uid;
    }

    public static String get(Handle conn, String email) {
        return (conn.attach(UserAddresses.class).get(email));
    }

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

    interface IdInterface {
        @SqlUpdate(
                "INSERT INTO users (user_id) " +
                        "VALUES (:user_id)")
        void alloc(@Bind("user_id") String userId);
    }
}
