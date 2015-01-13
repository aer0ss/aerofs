package com.aerofs.polaris.api.types;

import com.aerofs.polaris.api.Filenames;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public final class Transform {

    @Min(1)
    private long logicalTimestamp = -1;

    @NotNull
    @Size(min = 1)
    private String originator = null;

    @JsonIgnore // not included in response
    private String root = null;

    @NotNull
    @Size(min = 1)
    private String oid = null;

    @NotNull
    private TransformType transformType = null;

    @Min(0)
    private long newVersion = -1;

    @Min(1)
    private long timestamp = -1;

    //
    // these parameters are set when the transform is part of an atomic operation
    //

    private String atomicOperationId = null;

    private int atomicOperationIndex = -1;

    private int atomicOperationTotal = -1;

    //
    // these parameters are set when the transform modifies a child
    //

    private String child = null;

    private ObjectType childObjectType = null;

    @Nullable
    private byte[] childName = null;

    //
    // these parameters are set when the transform modifies the content for an object
    //

    private String contentHash = null;

    private long contentSize = -1;

    private long contentMtime = -1;

    // constructor only includes parameters that must *always* be set
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

    private Transform() { }

    public void setAtomicOperationParameters(String atomicOperationId, int atomicOperationIndex, int atomicOperationTotal) {
        this.atomicOperationId = atomicOperationId;
        this.atomicOperationIndex = atomicOperationIndex;
        this.atomicOperationTotal = atomicOperationTotal;
    }

    public void setChildParameters(String child, @Nullable ObjectType childObjectType, @Nullable byte[] childName) {
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
    }

    public void setContentParameters(String hash, long size, long mtime) {
        this.contentHash = hash;
        this.contentSize = size;
        this.contentMtime = mtime;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    private void setLogicalTimestamp(long logicalTimestamp) {
        this.logicalTimestamp = logicalTimestamp;
    }

    public String getOriginator() {
        return originator;
    }

    private void setOriginator(String originator) {
        this.originator = originator;
    }

    public String getRoot() {
        return root;
    }

    private void setRoot(String root) {
        this.root = root;
    }

    public String getOid() {
        return oid;
    }

    private void setOid(String oid) {
        this.oid = oid;
    }

    public TransformType getTransformType() {
        return transformType;
    }

    private void setTransformType(TransformType transformType) {
        this.transformType = transformType;
    }

    public long getNewVersion() {
        return newVersion;
    }

    private void setNewVersion(long newVersion) {
        this.newVersion = newVersion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    private void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAtomicOperationId() {
        return atomicOperationId;
    }

    private void setAtomicOperationId(String atomicOperationId) {
        this.atomicOperationId = atomicOperationId;
    }

    public int getAtomicOperationIndex() {
        return atomicOperationIndex;
    }

    private void setAtomicOperationIndex(int atomicOperationIndex) {
        this.atomicOperationIndex = atomicOperationIndex;
    }

    public int getAtomicOperationTotal() {
        return atomicOperationTotal;
    }

    private void setAtomicOperationTotal(int atomicOperationTotal) {
        this.atomicOperationTotal = atomicOperationTotal;
    }

    public String getChild() {
        return child;
    }

    private void setChild(String child) {
        this.child = child;
    }

    public ObjectType getChildObjectType() {
        return childObjectType;
    }

    private void setChildObjectType(ObjectType childObjectType) {
        this.childObjectType = childObjectType;
    }

    @Nullable
    public String getChildName() {
        return Filenames.fromBytes(childName);
    }

    @JsonIgnore
    public byte[] getChildNameBytes() {
        return childName;
    }

    private void setChildName(String childName) {
        this.childName = Filenames.toBytes(childName);
    }

    public String getContentHash() {
        return contentHash;
    }

    private void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public long getContentSize() {
        return contentSize;
    }

    private void setContentSize(long contentSize) {
        this.contentSize = contentSize;
    }

    public long getContentMtime() {
        return contentMtime;
    }

    private void setContentMtime(long contentMtime) {
        this.contentMtime = contentMtime;
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
                && Arrays.equals(childName, other.childName)
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
