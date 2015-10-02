package com.aerofs.polaris.api.types;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Arrays;

@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public final class Transform {

    @Min(1)
    private long logicalTimestamp = -1;

    @NotNull
    private DID originator = null;

    // FIXME (AG): do not include in response - re-add @JsonIgnore
    private UniqueID store = null;

    @NotNull
    private UniqueID oid = null;

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

    private UniqueID child = null;

    private ObjectType childObjectType = null;

    @Nullable
    private byte[] childName = null;

    //
    // these parameters are set when the transform modifies the content for an object
    //

    private byte[] contentHash = Content.INVALID_HASH;

    private long contentSize = Content.INVALID_SIZE;

    private long contentMtime = Content.INVALID_MODIFICATION_TIME;

    //
    // this parameter is set when the transform is from a cross-store move
    //

    private UniqueID migrantOid = null;

    // constructor only includes parameters that must *always* be set
    public Transform(
            long logicalTimestamp,
            DID originator,
            UniqueID store,
            UniqueID oid,
            TransformType transformType,
            long newVersion,
            long timestamp) {
        this.logicalTimestamp = logicalTimestamp;
        this.originator = originator;
        this.store = store;
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

    public void setChildParameters(UniqueID child, @Nullable ObjectType childObjectType, @Nullable byte[] childName) {
        this.child = child;
        this.childObjectType = childObjectType;
        this.childName = childName;
    }

    public void setContentParameters(byte[] hash, long size, long mtime) {
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

    public DID getOriginator() {
        return originator;
    }

    private void setOriginator(DID originator) {
        this.originator = originator;
    }

    public UniqueID getStore() {
        return store;
    }

    private void setStore(UniqueID store) {
        this.store = store;
    }

    public UniqueID getOid() {
        return oid;
    }

    private void setOid(UniqueID oid) {
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

    public UniqueID getChild() {
        return child;
    }

    private void setChild(UniqueID child) {
        this.child = child;
    }

    public ObjectType getChildObjectType() {
        return childObjectType;
    }

    private void setChildObjectType(ObjectType childObjectType) {
        this.childObjectType = childObjectType;
    }

    @JsonSerialize(using = AeroTypes.UTF8StringSerializer.class)
    public @Nullable byte[] getChildName() {
        return childName;
    }

    @JsonDeserialize(using = AeroTypes.UTF8StringDeserializer.class)
    private void setChildName(@Nullable byte[] childName) {
        this.childName = childName;
    }

    @JsonSerialize(using = AeroTypes.Base16Serializer.class)
    public byte[] getContentHash() {
        return contentHash;
    }

    @JsonDeserialize(using = AeroTypes.Base16Deserializer.class)
    private void setContentHash(byte[] contentHash) {
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

    public UniqueID getMigrantOid() {
        return migrantOid;
    }

    public void setMigrantOid(UniqueID migrantOid) {
        this.migrantOid = migrantOid;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transform other = (Transform) o;
        return logicalTimestamp == other.logicalTimestamp
                && Objects.equal(originator, other.originator)
                && Objects.equal(store, other.store)
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
                && Arrays.equals(contentHash, other.contentHash)
                && contentSize == other.contentSize
                && contentMtime == other.contentMtime
                && Objects.equal(migrantOid, other.migrantOid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                logicalTimestamp,
                originator,
                store,
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
                contentMtime,
                migrantOid);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("databaseTimestamp", logicalTimestamp)
                .add("originator", originator)
                .add("store", store)
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
                .add("migrantOid", migrantOid)
                .toString();
    }
}
