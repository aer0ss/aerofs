package com.aerofs.polaris.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public final class Transform {

    @Min(1)
    public final long logicalTimestamp;

    @NotNull
    @Size(min = 1)
    public final String originator;

    @JsonIgnore
    public final String root;

    @NotNull
    @Size(min = 1)
    public final String oid;

    @NotNull
    public final TransformType transformType;

    @Min(0)
    public final long newVersion;

    @Min(1)
    public final long timestamp;

    //
    // these parameters are set when the transform is part of an atomic operation
    //

    public String atomicOperationId;

    public int atomicOperationIndex;

    public int atomicOperationTotal;

    //
    // these parameters are set when the transform modifies a child
    //

    public String child;

    public ObjectType childObjectType;

    public String childName;

    // do not *ever* use this constructor - used by Jackson reflection only!
    private Transform() {
        this.logicalTimestamp = 0;
        this.originator = null;
        this.root = null;
        this.oid = null;
        this.transformType = null;
        this.newVersion = -1; // 0 is a valid version!
        this.timestamp = 0;
        this.atomicOperationId = null;
        this.atomicOperationIndex = 0;
        this.atomicOperationTotal = 0;
        this.child = null;
        this.childObjectType = null;
        this.childName = null;
    }

    //
    // constructor only includes parameters that must *always* be set
    //

    public Transform(
            long logicalTimestamp,
            String originator,
            String root,
            String oid,
            TransformType transformType,
            long newVersion,
            long timestamp) {
        this.logicalTimestamp = logicalTimestamp;
        this.originator = originator;
        this.root = root;
        this.oid = oid;
        this.transformType = transformType;
        this.newVersion = newVersion;
        this.timestamp = timestamp;
    }

    public void setAtomicOperationParameters(String atomicOperationId, int atomicOperationIndex, int atomicOperationTotal) {
        this.atomicOperationId = atomicOperationId;
        this.atomicOperationIndex = atomicOperationIndex;
        this.atomicOperationTotal = atomicOperationTotal;
    }

    public void setChildParameters(String child, ObjectType childObjectType, @Nullable String childName) {
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transform other = (Transform) o;
        return logicalTimestamp == other.logicalTimestamp
                && originator.equals(other.originator)
                && root.equals(other.root)
                && oid.equals(other.oid)
                && transformType == other.transformType
                && newVersion == other.newVersion
                && timestamp == other.timestamp
                && Objects.equal(atomicOperationId, other.atomicOperationId)
                && atomicOperationIndex == other.atomicOperationIndex
                && atomicOperationTotal == other.atomicOperationTotal
                && Objects.equal(child, other.child)
                && childObjectType == other.childObjectType
                && Objects.equal(childName, other.childName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(logicalTimestamp, originator, root, oid, transformType, newVersion, timestamp, atomicOperationId, atomicOperationIndex, atomicOperationTotal, child, childObjectType, childName);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("logicalTimestamp", logicalTimestamp)
                .add("originator", originator)
                .add("root", root)
                .add("oid", oid)
                .add("transformType", transformType)
                .add("newVersion", newVersion)
                .add("timestamp", timestamp)
                .add("atomicOperationId", atomicOperationId)
                .add("atomicOperationIndex", atomicOperationIndex)
                .add("atomicOperationTotal", atomicOperationTotal)
                .add("child", child)
                .add("childObjectType", childObjectType)
                .add("childName", childName)
                .toString();
    }
}
