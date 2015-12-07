package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.Transforms;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.google.common.base.Objects;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public final class TestTransformsResource {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private final RequestSpecification verified = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);
    private static MySQLDatabase database = new MySQLDatabase("test");

    @ClassRule
    public static RuleChain polaris = RuleChain.outerRule(database).around(new PolarisTestServer());

    @After
    public void afterTest() throws Exception {
        database.clear();
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsInserted() {
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(1L));

        Transform transform = applied.transforms.get(0);
        assertThat(transform, matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1", null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsRemoved() {
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");
        PolarisHelpers.removeFileOrFolder(verified, store, folder);

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.REMOVE_CHILD, 2, folder, null, null, null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsMoved() {
        SID store = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(verified, store, "folder_1");
        OID folder2 = PolarisHelpers.newFolder(verified, store, "folder_2");
        OID file = PolarisHelpers.newFile(verified, folder1, "file");
        PolarisHelpers.moveFileOrFolder(verified, folder1, folder2, file, "renamed");

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(5));
        assertThat(applied.maxTransformCount, is(5L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2", null));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file", null));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, DEVICE, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed", null));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, DEVICE, folder1, TransformType.REMOVE_CHILD, 2, file, null, null, null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsRenamed() {
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");
        OID file = PolarisHelpers.newFile(verified, folder, "file");
        PolarisHelpers.moveFileOrFolder(verified, folder, folder, file, "renamed");

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(3));
        assertThat(applied.maxTransformCount, is(3L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file", null));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder, TransformType.RENAME_CHILD, 2, file, ObjectType.FILE, "renamed", null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenContentIsAddedForAnObject() {
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        SID store = SID.generate();
        OID file = PolarisHelpers.newFile(verified, store, "file");
        PolarisHelpers.newFileContent(verified, file, 0, hash, 100, 1024);

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file", null));
        assertThat(applied.transforms.get(1), matchesContentTransform(2, DEVICE, file, 1, hash, 100, 1024));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenFolderIsShared() throws Exception {
        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, rootStore, "folder");

        PolarisHelpers.waitForJobCompletion(verified, PolarisHelpers.shareFolder(verified, rootStore, folder).jobID, 5);

        // note that the object type in the previous transform has also changed
        Transforms applied = PolarisHelpers.getTransforms(verified, rootStore, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, rootStore, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.SHARE, 1, null, null, null, null));

        SID sharedFolder = SID.folderOID2convertedStoreSID(folder);
        applied = PolarisHelpers.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, empty());
        assertThat(applied.maxTransformCount, is(2L));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnCorrectTransformsWhenNonemptyFolderIsShared()
            throws Exception
    {
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, rootStore, "folder");
        OID newFile = PolarisHelpers.newFile(verified, folder, "file");
        OID modifiedFile = PolarisHelpers.newFile(verified, folder, "file2");
        PolarisHelpers.newFileContent(verified, modifiedFile, 0, hash, 100, 1024);
        OID deletedFile = PolarisHelpers.newFile(verified, folder, "file3");
        PolarisHelpers.newFileContent(verified, deletedFile, 0, hash, 100, 1024);
        PolarisHelpers.removeFileOrFolder(verified, folder, deletedFile);

        PolarisHelpers.waitForJobCompletion(verified, PolarisHelpers.shareFolder(verified, rootStore, folder).jobID, 5);

        Transforms applied = PolarisHelpers.getTransforms(verified, rootStore, -1, 10);
        assertThat(applied.transforms, hasSize(8));
        assertThat(applied.maxTransformCount, is(13L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, rootStore, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.INSERT_CHILD, 1, newFile, ObjectType.FILE, "file", null));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder, TransformType.INSERT_CHILD, 2, modifiedFile, ObjectType.FILE, "file2", null));
        assertThat(applied.transforms.get(3), matchesContentTransform(4, DEVICE, modifiedFile, 1, hash, 100, 1024));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, DEVICE, folder, TransformType.INSERT_CHILD, 3, deletedFile, ObjectType.FILE, "file3", null));
        assertThat(applied.transforms.get(5), matchesContentTransform(6, DEVICE, deletedFile, 1, hash, 100, 1024));
        assertThat(applied.transforms.get(6), matchesMetaTransform(7, DEVICE, folder, TransformType.REMOVE_CHILD, 4, deletedFile, null, null, null));
        assertThat(applied.transforms.get(7), matchesMetaTransform(13, DEVICE, folder, TransformType.SHARE, 5, null, null, null, null));

        SID sharedFolder = SID.folderOID2convertedStoreSID(folder);
        applied = PolarisHelpers.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(5));
        assertThat(applied.maxTransformCount, is(13L));
        assertThat(applied.transforms, containsInAnyOrder(
                matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.INSERT_CHILD, newFile, ObjectType.FILE, "file"),
                matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.INSERT_CHILD, modifiedFile, ObjectType.FILE, "file2"),
                matchesReorderableContentTransform(DEVICE, modifiedFile, 1, hash, 100, 1024),
                matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.INSERT_CHILD, deletedFile, ObjectType.FILE, "file3"),
                matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.REMOVE_CHILD, deletedFile, null, null)));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenFileIsMigrated()
            throws Exception
    {
        byte[] hash1 = new byte[32], hash2 = new byte[32];
        Random random = new Random();
        SID store1 = SID.generate(), store2 = SID.generate();
        OID file = PolarisHelpers.newFile(verified, store1, "src_file");
        random.nextBytes(hash1);
        PolarisHelpers.newFileContent(verified, file, 0, hash1, 100, 1024);
        random.nextBytes(hash2);
        PolarisHelpers.newFileContent(verified, file, 1, hash2, 100, 2048);
        PolarisHelpers.moveFileOrFolder(verified, store1, store2, file, "dest_file");

        Transforms applied = PolarisHelpers.getTransforms(verified, store2, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(6L));
        assertThat(applied.transforms.get(0), matchesReorderableRandomChildOIDMetaTransform(DEVICE, store2, TransformType.INSERT_CHILD, ObjectType.FILE, "dest_file", file));
        assertThat(applied.transforms.get(1), matchesReorderableRandomOIDContentTransform(DEVICE, 2, hash2, 100, 2048));
        OID newOid = new OID(applied.transforms.get(0).getChild());

        applied = PolarisHelpers.getTransforms(verified, store1, -1, 10);
        assertThat(applied.transforms, hasSize(4));
        assertThat(applied.maxTransformCount, is(6L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "src_file", null));
        assertThat(applied.transforms.get(1), matchesContentTransform(2, DEVICE, file, 1, hash1, 100, 1024));
        assertThat(applied.transforms.get(2), matchesContentTransform(3, DEVICE, file, 2, hash2, 100, 2048));
        assertThat(applied.transforms.get(3), matchesMetaTransform(6, DEVICE, store1, TransformType.REMOVE_CHILD, 2, file, null, null, newOid));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnCorrectTransformsWhenFolderIsMigrated()
            throws Exception
    {
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        SID rootStore = SID.rootSID(USERID), share = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, rootStore, "folder");
        OID file1 = PolarisHelpers.newFile(verified, folder, "file1");
        OID modifiedFile = PolarisHelpers.newFile(verified, folder, "file2");
        PolarisHelpers.newFileContent(verified, modifiedFile, 0, hash, 100, 512);
        PolarisHelpers.newFileContent(verified, modifiedFile, 1, hash, 200, 1024);
        OID deletedFile = PolarisHelpers.newFile(verified, folder, "file3");
        PolarisHelpers.newFileContent(verified, deletedFile, 0, hash, 50, 2048);
        PolarisHelpers.removeFileOrFolder(verified, folder, deletedFile);

        OperationResult operationResult = PolarisHelpers.moveObject(verified, rootStore, share, folder, "folder")
                .assertThat().statusCode(HttpStatus.SC_OK)
                .and().extract().response().as(OperationResult.class);

        if (operationResult.jobID != null) {
            PolarisHelpers.waitForJobCompletion(verified, operationResult.jobID, 5);
        }

        Transforms applied = PolarisHelpers.getTransforms(verified, share, -1, 10);
        assertThat(applied.transforms, hasSize(6));
        assertThat(applied.maxTransformCount, is(15L));
        assertThat(applied.transforms, containsInAnyOrder(
                matchesReorderableRandomChildOIDMetaTransform(DEVICE, share, TransformType.INSERT_CHILD, ObjectType.FOLDER, "folder", folder),
                matchesReorderableRandomOIDsMetaTransform(DEVICE, TransformType.INSERT_CHILD, ObjectType.FILE, "file1", file1),
                matchesReorderableRandomOIDsMetaTransform(DEVICE, TransformType.INSERT_CHILD, ObjectType.FILE, "file2", modifiedFile),
                matchesReorderableRandomOIDsMetaTransform(DEVICE, TransformType.INSERT_CHILD, ObjectType.FILE, "file3", deletedFile),
                matchesReorderableRandomOIDContentTransform(DEVICE, 2, hash, 200, 1024),
                matchesReorderableRandomOIDsMetaTransform(DEVICE, TransformType.REMOVE_CHILD, null, null, null)));

        OID folderMigrant = null;
        for (Transform t : applied.transforms) {
            if (Objects.equal(t.getMigrantOid(), folder)) {
                folderMigrant = new OID(t.getChild());
            }
        }

        applied = PolarisHelpers.getTransforms(verified, rootStore, -1, 10);
        assertThat(applied.transforms, hasSize(9));
        assertThat(applied.maxTransformCount, is(15L));
        assertThat(applied.transforms.get(8), matchesMetaTransform(15, DEVICE, rootStore, TransformType.REMOVE_CHILD, 2, folder, null, null, folderMigrant));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenClientMigratesObject()
    {
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        SID store1 = SID.generate(), store2 = SID.generate();
        OID migrant = PolarisHelpers.newFile(verified, store1, "file");
        PolarisHelpers.newFileContent(verified, migrant, 0L, hash, 100, 512);
        OID newOID = OID.generate();
        PolarisHelpers.insertMigrant(verified, store2, newOID, "file", ObjectType.FILE, migrant);

        Transforms applied = PolarisHelpers.getTransforms(verified, store2, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(4L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(3, DEVICE, store2, TransformType.INSERT_CHILD, 1, newOID, ObjectType.FILE, "file", migrant));
        assertThat(applied.transforms.get(1), matchesContentTransform(4, DEVICE, newOID, 1L, hash, 100, 512));
    }

    @Test
    public void shouldReturnNoTransformsWhenDeviceHasReceivedAllAvailableTransforms() {
        SID store = SID.generate();
        OID folder1 = PolarisHelpers.newFolder(verified, store, "folder_1");
        OID folder2 = PolarisHelpers.newFolder(verified, store, "folder_2");
        OID file = PolarisHelpers.newFile(verified, folder1, "file");
        PolarisHelpers.moveFileOrFolder(verified, folder1, folder2, file, "renamed");

        Transforms applied;

        applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(5));
        assertThat(applied.maxTransformCount, is(5L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1", null));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2", null));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file", null));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, DEVICE, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed", null));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, DEVICE, folder1, TransformType.REMOVE_CHILD, 2, file, null, null, null));

        applied = PolarisHelpers.getTransforms(verified, store, 5, 10);
        assertThat(applied.transforms, empty()); // i.e. no transforms
        assertThat(applied.maxTransformCount, is(5L));
    }

    @Test
    public void shouldReturnBoundedListOfTransformsIfResultCountIsTooHigh() {
        SID store = SID.generate();
        OID[] folders = {
                PolarisHelpers.newFolder(verified, store, "folder_1"),
                PolarisHelpers.newFolder(verified, store, "folder_2"),
                PolarisHelpers.newFolder(verified, store, "folder_3"),
                PolarisHelpers.newFolder(verified, store, "folder_4"),
                PolarisHelpers.newFolder(verified, store, "folder_5"),
                PolarisHelpers.newFolder(verified, store, "folder_6"),
                PolarisHelpers.newFolder(verified, store, "folder_7"),
                PolarisHelpers.newFolder(verified, store, "folder_8"),
                PolarisHelpers.newFolder(verified, store, "folder_9"),
                PolarisHelpers.newFolder(verified, store, "folder_10"),
                PolarisHelpers.newFolder(verified, store, "folder_11"),
                PolarisHelpers.newFolder(verified, store, "folder_12"),
                PolarisHelpers.newFolder(verified, store, "folder_13"),
        };

        Transforms applied;
        int count;

        applied = PolarisHelpers.getTransforms(verified, store, -1, 100);
        assertThat(applied.transforms, hasSize(10));
        assertThat(applied.maxTransformCount, is(13L));

        count = 1;
        for (int i = 0; i < applied.transforms.size(); i++) {
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, DEVICE, store, TransformType.INSERT_CHILD, count + i, folders[i], ObjectType.FOLDER, "folder_" + (count + i), null));
        }

        applied = PolarisHelpers.getTransforms(verified, store, 10, 100);
        assertThat(applied.transforms, hasSize(3));
        assertThat(applied.maxTransformCount, is(13L));

        count = 11;
        for (int i = 0; i < applied.transforms.size(); i++) {
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, DEVICE, store, TransformType.INSERT_CHILD, count + i, folders[count + i - 1], ObjectType.FOLDER, "folder_" + (count + i), null));
        }
    }

    public static Matcher<? super Transform> matchesMetaTransform(
            final long logicalTimestamp,
            final DID originator,
            final UniqueID oid,
            final TransformType transformType,
            final long newVersion,
            @Nullable final OID child,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName,
            @Nullable OID migrant)
    {
        return new MetaTransformMatcher(logicalTimestamp, originator, oid, transformType, newVersion, child, childObjectType, childName, migrant, false, false, false);
    }

    public static Matcher<? super Transform> matchesReorderableMetaTransform(
            final DID originator,
            final UniqueID oid,
            final TransformType transformType,
            @Nullable final OID child,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName
    ) {
        return new MetaTransformMatcher(1L, originator, oid, transformType, 1L, child, childObjectType, childName, null, true, false, false);
    }

    private static Matcher<? super Transform> matchesReorderableRandomChildOIDMetaTransform(
            final DID originator,
            final UniqueID oid,
            final TransformType transformType,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName,
            @Nullable final OID migrant
    ) {
        return new MetaTransformMatcher(1L, originator, oid, transformType, 1L, null, childObjectType, childName, migrant, true, false, true);
    }

    private static Matcher<? super Transform> matchesReorderableRandomOIDsMetaTransform(
            final DID originator,
            final TransformType transformType,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName,
            @Nullable final OID migrant
    ) {
        return new MetaTransformMatcher(1L, originator, null, transformType, 1L, null, childObjectType, childName, migrant, true, true, true);
    }


    public static Matcher<? super Transform> matchesContentTransform(
            final long logicalTimestamp,
            final DID originator,
            final UniqueID oid,
            final long newVersion,
            final byte[] contentHash,
            final long contentSize,
            final long contentMtime
    ) {
        return new ContentTransformMatcher(logicalTimestamp, originator, oid, newVersion, contentHash, contentSize, contentMtime, false, false);
    }

    public static Matcher<? super Transform> matchesReorderableContentTransform(
            final DID originator,
            final UniqueID oid,
            final long newVersion,
            final byte[] contentHash,
            final long contentSize,
            final long contentMtime
    )   {
        return new ContentTransformMatcher(1L, originator, oid, newVersion, contentHash, contentSize, contentMtime, true, false);
    }

    private static Matcher<? super Transform> matchesReorderableRandomOIDContentTransform(
            final DID originator,
            final long newVersion,
            final byte[] contentHash,
            final long contentSize,
            final long contentMtime
    ) {
        return new ContentTransformMatcher(1L, originator, null, newVersion, contentHash, contentSize, contentMtime, true, true);
    }

    private static class MetaTransformMatcher extends TypeSafeMatcher<Transform> {
        final long logicalTimestamp;
        final DID originator;
        final UniqueID oid;
        final TransformType transformType;
        final long newVersion;
        final OID child;
        final ObjectType childObjectType;
        final String childName;
        final OID migrant;
        final boolean ignoreTimestamp;
        final boolean ignoreOID;
        final boolean ignoreChildOID;

        public MetaTransformMatcher(
                long logicalTimestamp,
                final DID originator,
                final UniqueID oid,
                final TransformType transformType,
                final long newVersion,
                @Nullable final OID child,
                @Nullable final ObjectType childObjectType,
                @Nullable final String childName,
                @Nullable final OID migrant,
                boolean ignoreTimestamp,
                boolean ignoreOID,
                boolean ignoreChildOID
        )   {
            this.logicalTimestamp = logicalTimestamp;
            this.originator = originator;
            this.oid = oid;
            this.transformType = transformType;
            this.newVersion = newVersion;
            this.child = child;
            this.childObjectType = childObjectType;
            this.childName = childName;
            this.migrant = migrant;
            this.ignoreTimestamp = ignoreTimestamp;
            this.ignoreOID = ignoreOID;
            this.ignoreChildOID = ignoreChildOID;
        }

        @Override
        protected boolean matchesSafely(Transform item) {
            return Objects.equal(originator, item.getOriginator())
                    && (ignoreTimestamp || logicalTimestamp == item.getLogicalTimestamp())
                    && (ignoreOID ||  Objects.equal(oid, item.getOid()))
                    && transformType == item.getTransformType()
                    && (ignoreTimestamp || newVersion == item.getNewVersion())
                    && (ignoreChildOID || Objects.equal(child, item.getChild()))
                    && Objects.equal(childObjectType, item.getChildObjectType())
                    && Objects.equal(childName, PolarisUtilities.stringFromUTF8Bytes(item.getChildName()))
                    && Objects.equal(migrant, item.getMigrantOid())
                    && item.getContentHash() == null
                    && item.getContentSize() == -1
                    && item.getContentMtime() == -1;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("meta transform has properties: ");
            if (!ignoreTimestamp) {
                description.appendText(String.format("timestamp:%d newVersion:%d ", logicalTimestamp, newVersion));
            } else {
                description.appendText("timestamp:ignored newVersion:ignored ");
            }
            if (!ignoreOID) {
                description.appendText(String.format("oid:%s ", oid));
            } else {
                description.appendText("oid:ignored");
            }
            if (!ignoreChildOID) {
                description.appendText(String.format("child:%s ", child));
            } else {
                description.appendText("child:ignored");
            }

            description.appendText(String.format("originator:%s transformType:%s childObjectType:%s childName:%s migrant:%s", originator, transformType, childObjectType, childName, migrant));
        }
    }

    private static class ContentTransformMatcher extends TypeSafeMatcher<Transform> {
        final long logicalTimestamp;
        final DID originator;
        final UniqueID oid;
        final long newVersion;
        final byte[] contentHash;
        final long contentSize;
        final long contentMtime;
        final boolean ignoreTimestamp;
        final boolean ignoreOID;

        public ContentTransformMatcher(
                long logicalTimestamp,
                final DID originator,
                final UniqueID oid,
                final long newVersion,
                final byte[] contentHash,
                final long contentSize,
                final long contentMtime,
                boolean ignoreTimestamp,
                boolean ignoreOID
        )   {
            this.logicalTimestamp = logicalTimestamp;
            this.originator = originator;
            this.oid = oid;
            this.newVersion = newVersion;
            this.contentHash = contentHash;
            this.contentSize = contentSize;
            this.contentMtime = contentMtime;
            this.ignoreTimestamp = ignoreTimestamp;
            this.ignoreOID = ignoreOID;
        }

        @Override
        protected boolean matchesSafely(Transform item) {
            return Objects.equal(originator, item.getOriginator())
                    && (ignoreTimestamp || logicalTimestamp == item.getLogicalTimestamp())
                    && (ignoreOID ||  Objects.equal(oid, item.getOid()))
                    && item.getTransformType() == TransformType.UPDATE_CONTENT
                    && newVersion == item.getNewVersion()
                    && item.getChild() == null
                    && item.getChildObjectType() == null
                    && item.getChildName() == null
                    && item.getMigrantOid() == null
                    && Arrays.equals(contentHash, item.getContentHash())
                    && contentSize == item.getContentSize()
                    && contentMtime == item.getContentMtime();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("content transform has properties: ");
            if (!this.ignoreTimestamp) {
                description.appendText(String.format("timestamp:%d ", logicalTimestamp));
            } else {
                description.appendText("timestamp:ignored ");
            }
            if (!this.ignoreOID) {
                description.appendText(String.format("oid:%s ", oid));
            } else {
                description.appendText("oid:ignored ");
            }
            description.appendText(String.format("originator:%s newVersion:%d contentHash:%s contentSize:%s contentMtime:%s",
                    originator,
                    newVersion,
                    Arrays.toString(contentHash),
                    contentSize,
                    contentMtime));
        }
    }
}
