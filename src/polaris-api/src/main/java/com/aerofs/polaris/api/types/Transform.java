package com.aerofs.polaris.api.types;

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
    public long logicalTimestamp = -1;

    @NotNull
    @Size(min = 1)
    public String originator = null;

    @JsonIgnore // not included in response
    public String root = null;

    @NotNull
    @Size(min = 1)
    public String oid = null;

    @NotNull
    public TransformType transformType = null;

    @Min(0)
    public long newVersion = -1;

    @Min(1)
    public long timestamp = -1;

    //
    // these parameters are set when the transform is part of an atomic operation
    //

    public String atomicOperationId = null;

    public int atomicOperationIndex = -1;

    public int atomicOperationTotal = -1;

    //
    // these parameters are set when the transform modifies a child
    //

    public String child = null;

    public ObjectType childObjectType = null;

    public String childName = null;

    //
    // these parameters are set when the transform modifies the content for an object
    //

    public String contentHash = null;

    public long contentSize = -1;

    public long contentMtime = -1;

    /**
     * For Jackson use only - do not use directly.
     */
    @SuppressWarnings("unused")
    private Transform() { }

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

    public void setChildParameters(String child, @Nullable ObjectType childObjectType, @Nullable String childName) {
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
    }

    public void setContentParameters(String hash, long size, long mtime) {
        this.contentHash = hash;
        this.contentSize = size;
        this.contentMtime = mtime;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transform other = (Transform) o;
        return logicalTimestamp == other.logicalTimestamp
                && Objects.equal(originator, other.originator)
                && Objects.equal(root, other.root)
                && Objects.equal(oid, other.oid)
                && Objects.equal(transformType, other.transformType)
                && newVersion == other.newVersion
                && timestamp == other.timestamp
                && Objects.equal(atomicOperationId, other.atomicOperationId)
                && atomicOperationIndex == other.atomicOperationIndex
                && atomicOperationTotal == other.atomicOperationTotal
                && Objects.equal(child, other.child)
                && Objects.equal(childObjectType, other.childObjectType)
                && Objects.equal(childName, other.childName)
                && Objects.equal(contentHash, other.contentHash)
                && contentSize == other.contentSize
                && contentMtime == other.contentMtime;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                logicalTimestamp,
                originator,
                root,
                oid,
                transformType,
                newVersion,
                timestamp,
                atomicOperationId,
                atomicOperationIndex,
                atomicOperationTotal,
                child,
                childObjectType,
                childName,
                contentHash,
                contentSize,
                contentMtime);
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
                .add("contentHash", contentHash)
                .add("contentSize", contentSize)
                .add("contentMtime", contentMtime)
                .toString();
    }
}
