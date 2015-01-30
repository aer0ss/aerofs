package com.aerofs.polaris.sparta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.List;

// FIXME (AG): remove when the rest-api module no longer depends on restless
class Member {

    private final String email;
    private final String firstName;
    private final String lastName;
    private final List<String> permissions;

    @JsonCreator
    public Member(
            @JsonProperty("email") String email,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("permissions") List<String> permissions) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.permissions = permissions;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Member other = (Member) o;

        return Objects.equal(email, other.email) &&
                Objects.equal(firstName, other.firstName) &&
                Objects.equal(lastName, other.lastName) &&
                Objects.equal(permissions, other.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email, firstName, lastName, permissions);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("email", email)
                .add("firstName", firstName)
                .add("lastName", lastName)
                .add("permissions", permissions)
                .toString();
    }
}
