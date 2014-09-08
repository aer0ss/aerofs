package com.aerofs.polaris.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

public final class LogicalObject {

    // this property exists when we pull it from the db
    // but we won't return it to the client (they don't need the sid)
    @JsonIgnore
    public final String root;

    public final String oid;

    public final long version;

    public LogicalObject(String root, String oid, long version) {
        this.root = root;
        this.oid = oid;
        this.version = version;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LogicalObject other = (LogicalObject) o;
        return root.equals(other.root) && oid.equals(other.oid) && version == other.version;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(root, oid, version);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("root", root)
                .add("oid", oid)
                .add("version", version)
                .toString();
    }
}
