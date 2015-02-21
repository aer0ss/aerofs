package com.aerofs.polaris.api;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.polaris.api.batch.location.LocationBatch;
import com.aerofs.polaris.api.batch.location.LocationBatchOperation;
import com.aerofs.polaris.api.batch.location.LocationBatchOperationResult;
import com.aerofs.polaris.api.batch.location.LocationBatchResult;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;

public final class TestJsonConversion {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJsonConversion.class);
    private static final Random RANDOM = new Random();

    private final ObjectMapper mapper = new ObjectMapper();

    public TestJsonConversion() {
        mapper.registerModule(new PolarisModule());
    }

    @Test
    public void shouldSerializeAndDeserializeLocationBatch() throws IOException {
        List<LocationBatchOperation> operations = ImmutableList.of(
                new LocationBatchOperation(OID.generate(), RANDOM.nextInt(2000), DID.generate(), LocationUpdateType.INSERT),
                new LocationBatchOperation(OID.generate(), RANDOM.nextInt(2000), DID.generate(), LocationUpdateType.REMOVE),
                new LocationBatchOperation(OID.generate(), RANDOM.nextInt(2000), DID.generate(), LocationUpdateType.REMOVE),
                new LocationBatchOperation(OID.generate(), RANDOM.nextInt(2000), DID.generate(), LocationUpdateType.INSERT)
        );
        LocationBatch batch = new LocationBatch(operations);

        String serialized = mapper.writeValueAsString(batch);
        LOGGER.info("transform_batch:{}", serialized);

        LocationBatch deserialized = mapper.readValue(serialized, LocationBatch.class);
        assertThat(deserialized, Matchers.equalTo(batch));
    }

    @Test
    public void shouldSerializeAndDeserializeLocationBatchResult() throws IOException {
        List<LocationBatchOperationResult> results = ImmutableList.of(
                new LocationBatchOperationResult(PolarisError.INSUFFICIENT_PERMISSIONS, "message 0"),
                new LocationBatchOperationResult(PolarisError.INVALID_OPERATION_ON_TYPE, "message 1"),
                new LocationBatchOperationResult(PolarisError.NAME_CONFLICT, "message 2"),
                new LocationBatchOperationResult()
        );
        LocationBatchResult batch = new LocationBatchResult(results);

        String serialized = mapper.writeValueAsString(batch);
        LOGGER.info("location_batch_result:{}", serialized);

        LocationBatchResult deserialized = mapper.readValue(serialized, LocationBatchResult.class);
        assertThat(deserialized, Matchers.equalTo(batch));
    }

    @Test
    public void shouldSerializeAndDeserializeTransformBatch() throws IOException {
        List<TransformBatchOperation> operations = ImmutableList.of(
                new TransformBatchOperation(SID.generate(), new InsertChild(OID.generate(), ObjectType.FILE, "file")),
                new TransformBatchOperation(SID.generate(), new RemoveChild(OID.generate())),
                new TransformBatchOperation(OID.generate(), new MoveChild(OID.generate(), SID.generate(), "new")),
                new TransformBatchOperation(OID.generate(), new MoveChild(OID.generate(), OID.generate(), "new again")),
                new TransformBatchOperation(OID.generate(), new UpdateContent(1, "HASH".getBytes(Charsets.US_ASCII), 300, 11))
        );
        TransformBatch batch = new TransformBatch(operations);

        String serialized = mapper.writeValueAsString(batch);
        LOGGER.info("transform_batch:{}", serialized);

        TransformBatch deserialized = mapper.readValue(serialized, TransformBatch.class);
        assertThat(deserialized, Matchers.equalTo(batch));
    }

    @Test
    public void shouldSerializeAndDeserializeTransformBatchResult() throws IOException {
        List<TransformBatchOperationResult> results = ImmutableList.of(
                new TransformBatchOperationResult(PolarisError.INSUFFICIENT_PERMISSIONS, "message 1"),
                new TransformBatchOperationResult(PolarisError.NAME_CONFLICT, "message 2"),
                new TransformBatchOperationResult(ImmutableList.of(
                        new Updated(1, new LogicalObject(OID.generate(), OID.generate(), 2, ObjectType.FOLDER)),
                        new Updated(2, new LogicalObject(SID.generate(), OID.generate(), 1, ObjectType.FILE)),
                        new Updated(3, new LogicalObject(SID.generate(), OID.generate(), 1, ObjectType.FILE)))),
                new TransformBatchOperationResult(PolarisError.NO_SUCH_OBJECT, "message 3")
        );
        TransformBatchResult batch = new TransformBatchResult(results);

        String serialized = mapper.writeValueAsString(batch);
        LOGGER.info("transform_batch_result:{}", serialized);

        TransformBatchResult deserialized = mapper.readValue(serialized, TransformBatchResult.class);
        assertThat(deserialized, Matchers.equalTo(batch));
    }

    @Test
    public void shouldSerializeAndDeserializeChild() throws IOException {
        Child child = new Child(OID.generate(), ObjectType.FILE, "CHILD_NAME");

        String serialized = mapper.writeValueAsString(child);
        LOGGER.info("child:{}", serialized);

        Child deserialized = mapper.readValue(serialized, Child.class);
        assertThat(deserialized, Matchers.equalTo(child));
    }

    @Test
    public void shouldSerializeAndDeserializeEmptyContent() throws IOException {
        Content content = new Content(OID.generate(), 2039);

        String serialized = mapper.writeValueAsString(content);
        LOGGER.info("content:{}", serialized);

        Content deserialized = mapper.readValue(serialized, Content.class);
        assertThat(deserialized, Matchers.equalTo(content));
    }

    @Test
    public void shouldSerializeAndDeserializeContent() throws IOException {
        Random random = new Random();
        byte[] hash = new byte[32];
        random.nextBytes(hash);

        Content content = new Content(OID.generate(), 2039, hash, 293, 209382354);

        String serialized = mapper.writeValueAsString(content);
        LOGGER.info("content:{}", serialized);

        Content deserialized = mapper.readValue(serialized, Content.class);
        assertThat(deserialized, Matchers.equalTo(content));
    }

    @Test
    public void shouldSerializeAndDeserializeLogicalObject() throws IOException {
        LogicalObject object = new LogicalObject(SID.generate(), OID.generate(), 20394, ObjectType.MOUNT_POINT);

        String serialized = mapper.writeValueAsString(object);
        LOGGER.info("logical_object:{}", serialized);

        LogicalObject deserialized = mapper.readValue(serialized, LogicalObject.class);
        assertThat(deserialized, Matchers.equalTo(object));
    }

    @Test
    public void shouldSerializeAndDeserializeTransform() throws IOException {
        Transform transform = new Transform(129348, DID.generate(), SID.generate(), OID.generate(), TransformType.INSERT_CHILD, 23948, System.currentTimeMillis());

        String serialized = mapper.writeValueAsString(transform);
        LOGGER.info("transform:{}", serialized);

        Transform deserialized = mapper.readValue(serialized, Transform.class);
        assertThat(deserialized, Matchers.equalTo(transform));
    }

    @Test
    public void shouldSerializeAndDeserializeAtomicTransform() throws IOException {
        Transform transform = new Transform(129348, DID.generate(), SID.generate(), OID.generate(), TransformType.DELETE_CHILD, 23948, System.currentTimeMillis());
        transform.setAtomicOperationParameters("ID", 1, 2);

        String serialized = mapper.writeValueAsString(transform);
        LOGGER.info("atomic_transform:{}", serialized);

        Transform deserialized = mapper.readValue(serialized, Transform.class);
        assertThat(deserialized, Matchers.equalTo(transform));
    }

    @Test
    public void shouldSerializeAndDeserializeChildTransform() throws IOException {
        Transform transform = new Transform(129348, DID.generate(), SID.generate(), OID.generate(), TransformType.INSERT_CHILD, 23948, System.currentTimeMillis());
        transform.setChildParameters(OID.generate(), ObjectType.MOUNT_POINT, PolarisUtilities.stringToUTF8Bytes("filename"));

        String serialized = mapper.writeValueAsString(transform);
        LOGGER.info("child_transform:{}", serialized);

        Transform deserialized = mapper.readValue(serialized, Transform.class);
        assertThat(deserialized, Matchers.equalTo(transform));
    }

    @Test
    public void shouldSerializeAndDeserializeContentTransform() throws IOException {
        Random random = new Random();
        byte[] hash = new byte[32];
        random.nextBytes(hash);

        Transform transform = new Transform(129348, DID.generate(), SID.generate(), OID.generate(), TransformType.INSERT_CHILD, 23948, System.currentTimeMillis());
        transform.setContentParameters(hash, 2039, System.currentTimeMillis());

        String serialized = mapper.writeValueAsString(transform);
        LOGGER.info("content_transform:{}", serialized);

        Transform deserialized = mapper.readValue(serialized, Transform.class);
        assertThat(deserialized, Matchers.equalTo(transform));
    }

    @Test
    public void shouldSerializeAndDeserializeAllSingingAllDancingTransform() throws IOException {
        Random random = new Random();
        byte[] hash = new byte[32];
        random.nextBytes(hash);

        Transform transform = new Transform(129348, DID.generate(), SID.generate(), OID.generate(), TransformType.INSERT_CHILD, 23948, System.currentTimeMillis());
        transform.setAtomicOperationParameters("ID", 1, 2);
        transform.setChildParameters(OID.generate(), ObjectType.MOUNT_POINT, PolarisUtilities.stringToUTF8Bytes("filename"));
        transform.setContentParameters(hash, 2039, System.currentTimeMillis());

        String serialized = mapper.writeValueAsString(transform);
        LOGGER.info("complete_transform:{}", serialized);

        Transform deserialized = mapper.readValue(serialized, Transform.class);
        assertThat(deserialized, Matchers.equalTo(transform));
    }

    @Test
    public void shouldSerializeAndDeserializeInsertChild() throws IOException {
        InsertChild insertChild = new InsertChild(OID.generate(), ObjectType.FOLDER, "CHILD NAME");

        String serialized = mapper.writeValueAsString(insertChild);
        LOGGER.info("insert_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(insertChild));
    }

    @Test
    public void shouldSerializeAndDeserializeRemoveChild() throws IOException {
        RemoveChild removeChild = new RemoveChild(OID.generate());

        String serialized = mapper.writeValueAsString(removeChild);
        LOGGER.info("remove_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(removeChild));
    }

    @Test
    public void shouldSerializeAndDeserializeMoveChild() throws IOException {
        MoveChild moveChild = new MoveChild(OID.generate(), SID.generate(), "CHILD NAME");

        String serialized = mapper.writeValueAsString(moveChild);
        LOGGER.info("move_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(moveChild));
    }

    @Test
    public void shouldSerializeAndDeserializeUpdateContent() throws IOException {
        UpdateContent updateContent = new UpdateContent(1, "HASH".getBytes(Charsets.US_ASCII), 100, System.currentTimeMillis());

        String serialized = mapper.writeValueAsString(updateContent);
        LOGGER.info("update_content:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(updateContent));
    }
}