package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.operation.Transforms;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.google.common.base.Objects;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public final class TestTransformsResource {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private final RequestSpecification verified = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    @Rule
    public RuleChain polaris = RuleChain.outerRule(new MySQLDatabase("test")).around(new PolarisTestServer());

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsInserted() {
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(1L));

        Transform transform = applied.transforms.get(0);
        assertThat(transform, matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsRemoved() {
        SID store = SID.generate();
        OID folder = PolarisHelpers.newFolder(verified, store, "folder_1");
        PolarisHelpers.removeFileOrFolder(verified, store, folder);

        Transforms applied = PolarisHelpers.getTransforms(verified, store, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.REMOVE_CHILD, 2, folder, null, null));
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

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, DEVICE, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed"));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, DEVICE, folder1, TransformType.REMOVE_CHILD, 2, file, null, null));
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

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder, TransformType.RENAME_CHILD, 2, file, ObjectType.FILE, "renamed"));
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

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(1), matchesContentTransform(2, DEVICE, file, 1, hash, 100, 1024));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenFolderIsMigrated() {
        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, rootStore, "folder");

        PolarisHelpers.shareFolder(verified, folder);

        // note that the object type in the previous transform has also changed
        Transforms applied = PolarisHelpers.getTransforms(verified, rootStore, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, rootStore, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.SHARE, 1, null, null, null));

        SID sharedFolder = SID.folderOID2convertedStoreSID(folder);
        applied = PolarisHelpers.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, nullValue());
        assertThat(applied.maxTransformCount, is(0L));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenNonemptyFolderIsMigrated() {
        SID rootStore = SID.rootSID(USERID);
        OID folder = PolarisHelpers.newFolder(verified, rootStore, "folder");
        OID file = PolarisHelpers.newFile(verified, folder, "file");
        PolarisHelpers.shareFolder(verified, folder);

        Transforms applied = PolarisHelpers.getTransforms(verified, rootStore, -1, 10);
        assertThat(applied.transforms, hasSize(4));
        assertThat(applied.maxTransformCount, is(5L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, rootStore, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, folder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder, TransformType.SHARE, 2, null, null, null));
        assertThat(applied.transforms.get(3), matchesMetaTransform(5, DEVICE, folder, TransformType.REMOVE_CHILD, 3, file, null, null));

        SID sharedFolder = SID.folderOID2convertedStoreSID(folder);
        applied = PolarisHelpers.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(4L));
        assertThat(applied.transforms.get(0), matchesMetaTransform(4, DEVICE, sharedFolder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
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

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, DEVICE, store, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, DEVICE, store, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, DEVICE, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, DEVICE, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed"));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, DEVICE, folder1, TransformType.REMOVE_CHILD, 2, file, null, null));

        applied = PolarisHelpers.getTransforms(verified, store, 5, 10);
        assertThat(applied.transforms, nullValue()); // i.e. no transforms
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
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, DEVICE, store, TransformType.INSERT_CHILD, count + i, folders[i], ObjectType.FOLDER, "folder_" + (count + i)));
        }

        applied = PolarisHelpers.getTransforms(verified, store, 10, 100);
        assertThat(applied.transforms, hasSize(3));
        assertThat(applied.maxTransformCount, is(13L));

        count = 11;
        for (int i = 0; i < applied.transforms.size(); i++) {
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, DEVICE, store, TransformType.INSERT_CHILD, count + i, folders[count + i - 1], ObjectType.FOLDER, "folder_" + (count + i)));
        }
    }

    private static Matcher<? super Transform> matchesMetaTransform(
            final long logicalTimestamp,
            final DID originator,
            final UniqueID oid,
            final TransformType transformType,
            final long newVersion,
            @Nullable final OID child,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName
    ) {
        return new TypeSafeMatcher<Transform>() {
            @Override
            protected boolean matchesSafely(Transform item) {
                return logicalTimestamp == item.getLogicalTimestamp()
                        && Objects.equal(originator, item.getOriginator())
                        && Objects.equal(oid, item.getOid())
                        && transformType == item.getTransformType()
                        && newVersion == item.getNewVersion()
                        && Objects.equal(child, item.getChild())
                        && Objects.equal(childObjectType, item.getChildObjectType())
                        && Objects.equal(childName, PolarisUtilities.stringFromUTF8Bytes(item.getChildName()))
                        && item.getContentHash() == null
                        && item.getContentSize() == -1
                        && item.getContentMtime() == -1;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("meta transform has properties: logicalTimestamp:%d originator:%s oid:%s transformType:%s newVersion:%d child:%s childObjectType:%s childName:%s",
                        logicalTimestamp,
                        originator,
                        oid,
                        transformType,
                        newVersion,
                        child,
                        childObjectType,
                        childName));
            }
        };
    }

    private static Matcher<? super Transform> matchesContentTransform(
            final long logicalTimestamp,
            final DID originator,
            final UniqueID oid,
            final long newVersion,
            final byte[] contentHash,
            final long contentSize,
            final long contentMtime
    ) {
        return new TypeSafeMatcher<Transform>() {
            @Override
            protected boolean matchesSafely(Transform item) {
                return logicalTimestamp == item.getLogicalTimestamp()
                        && Objects.equal(originator, item.getOriginator())
                        && Objects.equal(oid, item.getOid())
                        && item.getTransformType() == TransformType.UPDATE_CONTENT
                        && newVersion == item.getNewVersion()
                        && item.getChild() == null
                        && item.getChildObjectType() == null
                        && item.getChildName() == null
                        && Arrays.equals(contentHash, item.getContentHash())
                        && contentSize == item.getContentSize()
                        && contentMtime == item.getContentMtime();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("content transform has properties: logicalTimestamp:%d originator:%s oid:%s newVersion:%d contentHash:%s contentSize:%s contentMtime:%s",
                        logicalTimestamp,
                        originator,
                        oid,
                        newVersion,
                        Arrays.toString(contentHash),
                        contentSize,
                        contentMtime));
            }
        };
    }
}
