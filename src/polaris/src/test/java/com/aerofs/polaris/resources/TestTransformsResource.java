package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.TestUtilities;
import com.aerofs.polaris.api.operation.AppliedTransforms;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public final class TestTransformsResource {

    static {
        RestAssured.config = TestUtilities.newRestAssuredConfig();
    }

    private final String device = Identifiers.newRandomDevice();
    private final RequestSpecification verified = TestUtilities.newVerifiedAeroUserSpecification(device, "test@aerofs.com");

    @Rule
    public RuleChain polaris = RuleChain.outerRule(new MySQLDatabase("test")).around(new PolarisTestServer());

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsInserted() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, sharedFolder, "folder_1");

        AppliedTransforms applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(1L));

        Transform transform = applied.transforms.get(0);
        assertThat(transform, matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsRemoved() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, sharedFolder, "folder_1");
        TestUtilities.removeFileOrFolder(verified, sharedFolder, folder);

        AppliedTransforms applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, device, sharedFolder, TransformType.REMOVE_CHILD, 2, folder, null, null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsMoved() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String folder1 = TestUtilities.newFolder(verified, sharedFolder, "folder_1");
        String folder2 = TestUtilities.newFolder(verified, sharedFolder, "folder_2");
        String file = TestUtilities.newFile(verified, folder1, "file");
        TestUtilities.moveFileOrFolder(verified, folder1, folder2, file, "renamed");

        AppliedTransforms applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(5));
        assertThat(applied.maxTransformCount, is(5L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, device, sharedFolder, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, device, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, device, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed"));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, device, folder1, TransformType.REMOVE_CHILD, 2, file, null, null));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenAnObjectIsRenamed() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String folder = TestUtilities.newFolder(verified, sharedFolder, "folder_1");
        String file = TestUtilities.newFile(verified, folder, "file");
        TestUtilities.moveFileOrFolder(verified, folder, folder, file, "renamed");

        AppliedTransforms applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(3));
        assertThat(applied.maxTransformCount, is(3L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, folder, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, device, folder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, device, folder, TransformType.RENAME_CHILD, 2, file, ObjectType.FILE, "renamed"));
    }

    @Test
    public void shouldReturnCorrectTransformsWhenContentIsAddedForAnObject() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String file = TestUtilities.newFile(verified, sharedFolder, "file");
        TestUtilities.newFileContent(verified, file, 0, "HASH", 100, 1024);

        AppliedTransforms applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(1), matchesContentTransform(2, device, file, 1, "HASH", 100, 1024));
    }

    @Test
    public void shouldReturnNoTransformsWhenDeviceHasReceivedAllAvailableTransforms() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String folder1 = TestUtilities.newFolder(verified, sharedFolder, "folder_1");
        String folder2 = TestUtilities.newFolder(verified, sharedFolder, "folder_2");
        String file = TestUtilities.newFile(verified, folder1, "file");
        TestUtilities.moveFileOrFolder(verified, folder1, folder2, file, "renamed");

        AppliedTransforms applied;

        applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 10);
        assertThat(applied.transforms, hasSize(5));
        assertThat(applied.maxTransformCount, is(5L));

        assertThat(applied.transforms.get(0), matchesMetaTransform(1, device, sharedFolder, TransformType.INSERT_CHILD, 1, folder1, ObjectType.FOLDER, "folder_1"));
        assertThat(applied.transforms.get(1), matchesMetaTransform(2, device, sharedFolder, TransformType.INSERT_CHILD, 2, folder2, ObjectType.FOLDER, "folder_2"));
        assertThat(applied.transforms.get(2), matchesMetaTransform(3, device, folder1, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "file"));
        assertThat(applied.transforms.get(3), matchesMetaTransform(4, device, folder2, TransformType.INSERT_CHILD, 1, file, ObjectType.FILE, "renamed"));
        assertThat(applied.transforms.get(4), matchesMetaTransform(5, device, folder1, TransformType.REMOVE_CHILD, 2, file, null, null));

        applied = TestUtilities.getTransforms(verified, sharedFolder, 5, 10);
        assertThat(applied.transforms, nullValue()); // i.e. no transforms
        assertThat(applied.maxTransformCount, is(5L));
    }

    @Test
    public void shouldReturnBoundedListOfTransformsIfResultCountIsTooHigh() {
        String sharedFolder = Identifiers.newRandomSharedFolder();
        String[] folders = {
                TestUtilities.newFolder(verified, sharedFolder, "folder_1"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_2"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_3"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_4"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_5"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_6"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_7"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_8"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_9"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_10"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_11"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_12"),
                TestUtilities.newFolder(verified, sharedFolder, "folder_13"),
        };

        AppliedTransforms applied;
        int count;

        applied = TestUtilities.getTransforms(verified, sharedFolder, -1, 100);
        assertThat(applied.transforms, hasSize(10));
        assertThat(applied.maxTransformCount, is(13L));

        count = 1;
        for (int i = 0; i < applied.transforms.size(); i++) {
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, device, sharedFolder, TransformType.INSERT_CHILD, count + i, folders[i], ObjectType.FOLDER, "folder_" + (count + i)));
        }

        applied = TestUtilities.getTransforms(verified, sharedFolder, 10, 100);
        assertThat(applied.transforms, hasSize(3));
        assertThat(applied.maxTransformCount, is(13L));

        count = 11;
        for (int i = 0; i < applied.transforms.size(); i++) {
            assertThat(applied.transforms.get(i), matchesMetaTransform(count + i, device, sharedFolder, TransformType.INSERT_CHILD, count + i, folders[count + i - 1], ObjectType.FOLDER, "folder_" + (count + i)));
        }
    }

    private static Matcher<? super Transform> matchesMetaTransform(
            final long logicalTimestamp,
            final String originator,
            final String oid,
            final TransformType transformType,
            final long newVersion,
            @Nullable final String child,
            @Nullable final ObjectType childObjectType,
            @Nullable final String childName
    ) {
        return new TypeSafeMatcher<Transform>() {
            @Override
            protected boolean matchesSafely(Transform item) {
                return logicalTimestamp == item.logicalTimestamp
                        && Objects.equal(originator, item.originator)
                        && Objects.equal(oid, item.oid)
                        && transformType == item.transformType
                        && newVersion == item.newVersion
                        && Objects.equal(child, item.child)
                        && Objects.equal(childObjectType, item.childObjectType)
                        && Objects.equal(childName, item.childName)
                        && item.contentHash == null
                        && item.contentSize == -1
                        && item.contentMtime == -1;
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
            final String originator,
            final String oid,
            final long newVersion,
            final String contentHash,
            final long contentSize,
            final long contentMtime
    ) {
        return new TypeSafeMatcher<Transform>() {
            @Override
            protected boolean matchesSafely(Transform item) {
                return logicalTimestamp == item.logicalTimestamp
                        && Objects.equal(originator, item.originator)
                        && Objects.equal(oid, item.oid)
                        && item.transformType == TransformType.UPDATE_CONTENT
                        && newVersion == item.newVersion
                        && item.child == null
                        && item.childObjectType == null
                        && item.childName == null
                        && Objects.equal(contentHash, item.contentHash)
                        && contentSize == item.contentSize
                        && contentMtime == item.contentMtime;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("content transform has properties: logicalTimestamp:%d originator:%s oid:%s newVersion:%d contentHash:%s contentSize:%s contentMtime:%s",
                        logicalTimestamp,
                        originator,
                        oid,
                        newVersion,
                        contentHash,
                        contentSize,
                        contentMtime));
            }
        };
    }
}
