package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.Transforms;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.annotation.Nullable;

import static org.apache.http.HttpStatus.SC_OK;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static com.jayway.restassured.RestAssured.given;

public class TestConversionResource {
    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    public static MySQLDatabase database = new MySQLDatabase("test");
    public static PolarisTestServer polaris = new PolarisTestServer();

    @ClassRule
    public static RuleChain rule = RuleChain.outerRule(database).around(polaris);

    @After
    public void afterTest() throws Exception {
        database.clear();
    }

    @Test
    public void shouldIgnoreUpdatesIfVersionDoesNotDominate() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), nondominating = Maps.newHashMap(), conflictVersion = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        nondominating.put(device1, 1L);
        conflictVersion.put(device1, 2L);
        conflictVersion.put(device1, 1L);

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, version, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version))));

        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(2L));

        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "new_name_for_file", null, nondominating, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 200, 2048, nondominating)),
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "new_name_for_file", null, conflictVersion, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 200, 2048, conflictVersion))));

        submitBatchSuccessfully(store, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(2L));
    }

    @Test
    public void shouldHaveSeparateMetaAndContentVersions() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> metaVersion = Maps.newHashMap();
        Map<DID, Long> contentVersion = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        metaVersion.put(device1, 1L);
        metaVersion.put(device2, 2L);
        // content version is dominated by metaVersion
        contentVersion.put(device2, 1L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, metaVersion, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, contentVersion))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(2L));
    }

    @Test
    public void shouldIgnoreOperationsIfNotInsertChildOrUpdateContent() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, version, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(store, new RemoveChild(child))));

        TransformBatchResult result = submitBatch(store, batch);
        assertThat(result.results.size(), equalTo(3));
        assertThat(result.results.get(2).successful, equalTo(false));
        assertThat(result.results.get(2).error.errorCode, equalTo(PolarisError.UNKNOWN));
    }

    @Test
    public void shouldCreateNewOIDsForOIDConflict() throws Exception
    {
        SID store1 = SID.generate(), store2 = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store1, insertChild(child, ObjectType.FILE, "in_store1", null, version, null))));

        submitBatchSuccessfully(store1, batch);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store2, insertChild(child, ObjectType.FILE, "in_store2", null, version, null))));
        submitBatchSuccessfully(store2, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store1, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store2, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        // need to have generate a new OID since OIDs in phoenix are globally unique
        assertThat(t.transforms.get(0).getChild(), not(child));
    }

    @Test
    public void shouldOverrideNonConversionTransforms() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, version, null)),
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version))));

        submitBatchSuccessfully(store, batch);

        random.nextBytes(hash);
        PolarisHelpers.newFileContent(AUTHENTICATED, child, 1L, hash, 200, 2048);

        random.nextBytes(hash);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 400, 4096, version))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(4L));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.UPDATE_CONTENT));
        assertThat(t.transforms.get(3).getOid(), equalTo(child));
        assertThat(t.transforms.get(3).getContentHash(), equalTo(hash));
        assertThat(t.transforms.get(3).getContentSize(), equalTo(400L));
        assertThat(t.transforms.get(3).getContentMtime(), equalTo(4096L));
    }

    @Test
    public void shouldRenameOnNameConflictsWithDominatingVersion() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), conflict = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "object", null, version, null)),
                // same name under same parent, renames the first object
                new TransformBatchOperation(store, insertChild(conflict, ObjectType.FOLDER, "object", null, version, null))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(3L));
        assertThat(t.transforms.get(0).getOid(), equalTo(store));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
        assertThat(new String(t.transforms.get(0).getChildName()), equalTo("object"));
        assertThat(t.transforms.get(1).getOid(), equalTo(store));
        assertThat(t.transforms.get(1).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
        assertThat(new String(t.transforms.get(1).getChildName()), not("object"));
        assertThat(t.transforms.get(2).getOid(), equalTo(store));
        assertThat(t.transforms.get(2).getChild(), equalTo(conflict));
        assertThat(new String(t.transforms.get(2).getChildName()), equalTo("object"));
    }

    @Test
    public void shouldRenameIncomingOnNameConflictsWithNonDominatingVersion() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), conflict1 = OID.generate(), conflict2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), conflictVersion = Maps.newHashMap(), nonDomination = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        conflictVersion.put(device1, 2L);
        conflictVersion.put(device2, 1L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "object", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, conflictVersion, null)),
                new TransformBatchOperation(conflict1, insertChild(OID.generate(), ObjectType.FOLDER, "nestedFolder", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, nonDomination, null)),
                new TransformBatchOperation(conflict2, insertChild(OID.generate(), ObjectType.FOLDER, "nestedFolder", null, version, null))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(5L));
        assertThat(t.transforms.get(1).getOid(), equalTo(store));
        assertThat(t.transforms.get(1).getChild(), equalTo(conflict1));
        assertThat(new String(t.transforms.get(1).getChildName()), not("object"));
        assertThat(t.transforms.get(3).getOid(), equalTo(store));
        assertThat(t.transforms.get(3).getChild(), equalTo(conflict2));
        assertThat(new String(t.transforms.get(3).getChildName()), not("object"));
    }

    @Test
    public void shouldCreateMovesOnLaterInserts() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), parent = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "child", null, version, null)),
                new TransformBatchOperation(store, insertChild(parent, ObjectType.FOLDER, "parent", null, version, null)),
                // second insertion of child, but under a different parent
                new TransformBatchOperation(parent, insertChild(child, ObjectType.FOLDER, "child", null, version, null))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(4L));
        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(2).getOid(), equalTo(parent));
        assertThat(t.transforms.get(2).getChild(), equalTo(child));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.REMOVE_CHILD));
        assertThat(t.transforms.get(3).getOid(), equalTo(store));
        assertThat(t.transforms.get(3).getChild(), equalTo(child));
    }

    @Test
    public void shouldRejectOperationsNotOnPostedStore() throws Exception
    {
        SID store1 = SID.generate(), store2 = SID.generate();
        OID store1child = OID.generate(), store2file = OID.generate(), store2folder = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store1, insertChild(store1child, ObjectType.FOLDER, "child", null, version, null)),
                // an operation in a different store, should be rejected
                new TransformBatchOperation(store2, insertChild(store2file, ObjectType.FILE, "file", null, version, null))));

        TransformBatchResult result = submitBatch(store1, batch);
        assertThat(result.results.size(), equalTo(2));
        assertThat(result.results.get(1).successful, equalTo(false));
        assertThat(result.results.get(1).error.errorCode, equalTo(PolarisError.UNKNOWN));

        PolarisHelpers.newFileUsingOID(AUTHENTICATED, store2, store2file, "file");
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store2, new UpdateContent(0L, hash, 100, 1024, version))));
        // store mismatch
        result = submitBatch(store1, batch);
        assertThat(result.results.size(), equalTo(1));
        assertThat(result.results.get(0).successful, equalTo(false));
        assertThat(result.results.get(0).error.errorCode, equalTo(PolarisError.UNKNOWN));

        PolarisHelpers.newFolderUsingOID(AUTHENTICATED, store2, store2folder, "folder");
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store2, insertChild(store1child, ObjectType.FOLDER, "child", null, version, null))));
        // store mismatch
        result = submitBatch(store1, batch);
        assertThat(result.results.size(), equalTo(1));
        assertThat(result.results.get(0).successful, equalTo(false));
        assertThat(result.results.get(0).error.errorCode, equalTo(PolarisError.UNKNOWN));
    }

    @Test
    public void shouldBeAbleToMoveAnchorsInConversion() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), store = SID.generate();
        OID folder = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(store, ObjectType.STORE, "sharedfolder", null, version, null)),
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(folder, insertChild(store, ObjectType.STORE, "sharedfolder", null, version, null))));

        submitBatchSuccessfully(rootStore, batch);
    }

    @Test
    public void shouldTrackVersionsOfAliasAndTargetSeparately() throws Exception
    {
        SID store1 = SID.generate(), store2 = SID.generate();
        OID child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store1, insertChild(child, ObjectType.FILE, "in_store1", null, version, null))));

        submitBatchSuccessfully(store1, batch);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store2, insertChild(child, ObjectType.FILE, "in_store2", null, version, null))));
        submitBatchSuccessfully(store2, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store1, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store2, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        // need to have generate a new OID since OIDs in phoenix are globally unique
        assertThat(t.transforms.get(0).getChild(), not(child));
    }

    @Test
    public void shouldPersistSubmittedAliases() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), alias = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "object", null, version, Lists.newArrayList(alias))),
                new TransformBatchOperation(store, insertChild(alias, ObjectType.FOLDER, "object", null, version, null))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(1L));
        assertThat(t.transforms.get(0).getOid(), equalTo(store));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
        assertThat(new String(t.transforms.get(0).getChildName()), equalTo("object"));
    }

    @Test
    public void shouldFavorAliasOverNameConflict() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), alias = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(alias, ObjectType.FOLDER, "object", null, version, null)),
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "object", null, version, Lists.newArrayList(alias)))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(1L));
        assertThat(t.transforms.get(0).getOid(), equalTo(store));
        assertThat(t.transforms.get(0).getChild(), equalTo(alias));
        assertThat(new String(t.transforms.get(0).getChildName()), equalTo("object"));

        OID otherFolder =PolarisHelpers.newFolder(AUTHENTICATED, store, "folder");
        batch = new TransformBatch(Lists.newArrayList(
                // will move alias out of under the root to under folder
                new TransformBatchOperation(otherFolder, insertChild(child, ObjectType.FOLDER, "object", null, version, null))));
        submitBatchSuccessfully(store, batch);
        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(4L));
        assertThat(t.transforms.get(2).getOid(), equalTo(otherFolder));
        assertThat(t.transforms.get(2).getChild(), equalTo(alias));
        assertThat(new String(t.transforms.get(2).getChildName()), equalTo("object"));
        assertThat(t.transforms.get(3).getOid(), equalTo(store));
        assertThat(t.transforms.get(3).getChild(), equalTo(alias));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.REMOVE_CHILD));
    }

    @Test
    public void submittedAliasesShouldWorkWithOIDConflicts() throws Exception
    {
        SID store1 = SID.generate(), store2 = SID.generate();
        OID child = OID.generate(), alias = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store1, insertChild(child, ObjectType.FILE, "in_store1", null, version, null))));

        submitBatchSuccessfully(store1, batch);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store2, insertChild(alias, ObjectType.FILE, "in_store2", null, version, null)),
                new TransformBatchOperation(store2, insertChild(child, ObjectType.FILE, "in_store2", null, version, Lists.newArrayList(alias)))));
        submitBatchSuccessfully(store2, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store1, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store2, 0L, 10);
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getChild(), equalTo(alias));

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version))));
        submitBatchSuccessfully(store2, batch);
        t = PolarisHelpers.getTransforms(AUTHENTICATED, store2, 0L, 10);
        assertThat(t.transforms.size(), equalTo(2));
        assertThat(t.transforms.get(1).getOid(), equalTo(alias));
    }

    @Test
    public void previouslySubmittedObjectsRemovedUponFindingAlias() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), alias = OID.generate();
        Map<DID, Long> version1 = Maps.newHashMap(), version2 = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version1.put(device1, 1L);
        version1.put(device2, 2L);
        version2.put(device1, 2L);
        version2.put(device2, 2L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(alias, ObjectType.FOLDER, "name1", null, version2, null)),
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "name2", null, version1, null)),
                // object is already created, but now has to alias
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "name1", null, version1, Lists.newArrayList(alias)))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(3L));
        assertThat(t.transforms.get(2).getOid(), equalTo(store));
        assertThat(t.transforms.get(2).getChild(), equalTo(child));
        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.REMOVE_CHILD));

        // if the versions are switched a different set of transforms will result, but the end result is the same
        child = OID.generate();
        alias = OID.generate();
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(alias, ObjectType.FOLDER, "folder1", null, version1, null)),
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "folder2", null, version2, null)),
                // object is already created, but now has to alias
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "folder1", null, version2, Lists.newArrayList(alias)))));

        submitBatchSuccessfully(store, batch);
        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 3L, 10);
        assertThat(t.maxTransformCount, equalTo(7L));
        assertThat(t.transforms.size(), equalTo(4));
        assertThat(t.transforms.get(2).getOid(), equalTo(store));
        assertThat(t.transforms.get(2).getChild(), equalTo(alias));
        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.REMOVE_CHILD));
        assertThat(t.transforms.get(3).getOid(), equalTo(store));
        assertThat(t.transforms.get(3).getChild(), equalTo(child));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(new String(t.transforms.get(3).getChildName()), equalTo("folder1"));
    }

    @Test
    public void newAliasesAddedOnAllOperations() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), alias1 = OID.generate(), alias2 = OID.generate(), alias3 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), emptyVersion = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, version, null)),
                new TransformBatchOperation(store, insertChild(alias1, ObjectType.FILE, "file", null, version, Lists.newArrayList(child, alias2))),
                new TransformBatchOperation(store, insertChild(alias2, ObjectType.FILE, "file", null, version, Lists.newArrayList(child, alias3))),
                new TransformBatchOperation(store, insertChild(alias3, ObjectType.FILE, "file", null, emptyVersion, null))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(1L));
        assertThat(t.transforms.get(0).getOid(), equalTo(store));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
        assertThat(t.transforms.get(0).getTransformType(), equalTo(TransformType.INSERT_CHILD));

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias1, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias2, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias3, new UpdateContent(0L, hash, 100, 1024, version))));

        submitBatchSuccessfully(store, batch);
        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(2L));
        assertThat(t.transforms.get(1).getOid(), equalTo(child));
        assertThat(t.transforms.get(1).getTransformType(), equalTo(TransformType.UPDATE_CONTENT));
    }

    @Test
    public void shouldResolveMultipleAliases() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), alias1 = OID.generate(), alias2 = OID.generate(), alias3 = OID.generate(), alias4 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), emptyVersion = Maps.newHashMap(), higherVersion = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        higherVersion.put(device1, 2L);
        higherVersion.put(device2, 4L);
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                // higherVersion on alias1  to insure that it wins the resolution merges
                new TransformBatchOperation(store, insertChild(alias1, ObjectType.FILE, "file", null, higherVersion, null)),
                new TransformBatchOperation(store, insertChild(alias2, ObjectType.FILE, "file2", null, version, Lists.newArrayList(alias4))),
                new TransformBatchOperation(store, insertChild(alias3, ObjectType.FILE, "file3", null, emptyVersion, null)),
                new TransformBatchOperation(store, insertChild(child, ObjectType.FILE, "file", null, version, Lists.newArrayList(alias1, alias2, alias3)))));

        submitBatchSuccessfully(store, batch);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(5L));
        for (int i = 0; i < 5; i++) {
            if (i < 3) {
                assertThat(t.transforms.get(i).getTransformType(), equalTo(TransformType.INSERT_CHILD));
            } else {
                assertThat(t.transforms.get(i).getTransformType(), equalTo(TransformType.REMOVE_CHILD));
            }
        }

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias1, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias2, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias3, new UpdateContent(0L, hash, 100, 1024, version)),
                new TransformBatchOperation(alias4, new UpdateContent(0L, hash, 100, 1024, version))));

        submitBatchSuccessfully(store, batch);
        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(6L));
        assertThat(t.transforms.get(5).getTransformType(), equalTo(TransformType.UPDATE_CONTENT));
    }

    @Test
    public void shouldHandleUpdatesToRenamedObjects() throws Exception
    {
        SID store = SID.generate();
        OID original = OID.generate(), conflict1 = OID.generate(), c1child = OID.generate(), conflict2 = OID.generate(), c2child = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), conflictVersion = Maps.newHashMap(), dominating = Maps.newHashMap();
        DID device1 = DID.generate(), device2 = DID.generate();
        version.put(device1, 1L);
        version.put(device2, 2L);
        conflictVersion.put(device1, 2L);
        conflictVersion.put(device2, 1L);
        // dominates both versions
        dominating.put(device1, 3L);
        dominating.put(device2, 3l);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(original, ObjectType.FOLDER, "object", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, conflictVersion, null)),
                new TransformBatchOperation(conflict1, insertChild(c1child, ObjectType.FOLDER, "child", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, conflictVersion, null)),
                new TransformBatchOperation(conflict2, insertChild(c2child, ObjectType.FOLDER, "child", null, version, null))));
        submitBatchSuccessfully(store, batch);

        batch = new TransformBatch(Lists.newArrayList(
                // later insertion of the conflicted object in a way that doesn't lead to conflicts or objects getting renamed
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "othername", null, conflictVersion, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, dominating, null))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        // N.B. what really matters here is that conflict1 ends up as othername, and conflict2 as object - along with their children
        assertThat(t.maxTransformCount, equalTo(8L));
        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(2).getChild(), equalTo(c1child));
        assertThat(t.transforms.get(4).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(4).getChild(), equalTo(c2child));

        assertThat(t.transforms.get(5).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(5).getChild(), equalTo(conflict1));
        assertThat(new String(t.transforms.get(5).getChildName()), equalTo("othername"));
        assertThat(t.transforms.get(6).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(6).getChild(), equalTo(original));
        assertThat(new String(t.transforms.get(6).getChildName()), equalTo("object (2)"));
        assertThat(t.transforms.get(7).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(7).getChild(), equalTo(conflict2));
        assertThat(new String(t.transforms.get(7).getChildName()), equalTo("object"));
    }

    @Test
    public void canMoveExistingObjectIntoNameConflict() throws Exception
    {
        SID store = SID.generate();
        OID conflict1 = OID.generate(), conflict2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), lowerVersion = Maps.newHashMap();
        DID device = DID.generate();
        lowerVersion.put(device, 1L);
        version.put(device, 2L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "other name", null, lowerVersion, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, lowerVersion, null))));
        submitBatchSuccessfully(store, batch);

        // make sure the object can still be operated on
        batch = new TransformBatch(Lists.newArrayList(
            new TransformBatchOperation(conflict2, insertChild(OID.generate(), ObjectType.FILE, "file", null, version, null)),
            new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "new name", null, version, null))));
        submitBatchSuccessfully(store, batch);
    }

    @Test
    public void canHandleCascadingNameConflicts() throws Exception
    {
        SID store = SID.generate();
        OID conflict1 = OID.generate(), conflict2 = OID.generate(), conflict3 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object (2)", null, version, null)),
                new TransformBatchOperation(store, insertChild(conflict3, ObjectType.FOLDER, "object", null, version, null))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.transforms.size(), equalTo(4));

        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(2).getChild(), equalTo(conflict1));
        assertThat(new String(t.transforms.get(2).getChildName()), equalTo("object (3)"));

        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(3).getChild(), equalTo(conflict3));
        assertThat(new String(t.transforms.get(3).getChildName()), equalTo("object"));
    }

    @Test
    public void canHandleSuccessiveDominationsOnNameConflict() throws Exception
    {
        SID store = SID.generate();
        OID conflict1 = OID.generate(), conflict2 = OID.generate(), conflict3 = OID.generate();
        Map<DID, Long> version1 = Maps.newHashMap(), version2 = Maps.newHashMap(), version3 = Maps.newHashMap();
        DID device = DID.generate();
        version1.put(device, 1L);
        version2.put(device, 2L);
        version3.put(device, 3L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, version1, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, version2, null)),
                new TransformBatchOperation(store, insertChild(conflict3, ObjectType.FOLDER, "object", null, version3, null))));
        submitBatchSuccessfully(store, batch);

        version1.put(device, 4L);
        version2.put(device, 5L);
        // same version as before
        version3.put(device, 3L);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object", null, version1, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object", null, version2, null)),
                new TransformBatchOperation(store, insertChild(conflict3, ObjectType.FOLDER, "object", null, version3, null))));
        submitBatchSuccessfully(store, batch);

        version1.put(device, 6L);
        version2.put(device, 7L);
        // same version as before
        version3.put(device, 3L);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(conflict1, ObjectType.FOLDER, "object (2)", null, version1, null)),
                new TransformBatchOperation(store, insertChild(conflict2, ObjectType.FOLDER, "object (3)", null, version2, null)),
                new TransformBatchOperation(store, insertChild(conflict3, ObjectType.FOLDER, "object", null, version3, null))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 5L, 10);
        assertThat(t.maxTransformCount, equalTo(13L));

        Transform c1 = null, c2 = null, c3 = null;

        for (Transform transform : t.transforms) {
            if (transform.getChild().equals(conflict1)) {
                c1 = transform;
            } else if (transform.getChild().equals(conflict2)) {
                c2 = transform;
            } else if (transform.getChild().equals(conflict3)) {
                c3 = transform;
            }
        }

        assertThat(c1, notNullValue());
        assertThat(c2, notNullValue());
        assertThat(c3, notNullValue());
        assertThat(c1.getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(new String(c1.getChildName()), equalTo("object (2)"));
        assertThat(c2.getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(new String(c2.getChildName()), equalTo("object (3)"));
        assertThat(c3.getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(new String(c3.getChildName()), equalTo("object"));
    }

    @Test
    public void shouldAliasFolderToSharedFolder() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), sharedFolder = SID.generate();
        OID folder = SID.convertedStoreSID2folderOID(sharedFolder), child = OID.generate();
        Map<DID, Long> emptyVersion = Maps.newHashMap(), version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder", null, emptyVersion, null)),
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "shared_folder", null, version, null)),
                new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(2L));
        assertThat(t.transforms.get(0).getOid(), equalTo(sharedFolder));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        sharedFolder = SID.generate();
        folder = SID.convertedStoreSID2folderOID(sharedFolder);
        child = OID.generate();
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder2", null, emptyVersion, null)),
                // can cause a rename, should not depend on a name conflict
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "different_name", null, version, null)),
                new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(5L));
        assertThat(t.transforms.get(0).getOid(), equalTo(sharedFolder));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
    }

    @Test
    public void shouldShareFolderUponReceivingSharedFolderUpdate() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), sharedFolder = SID.generate();
        OID folder = SID.convertedStoreSID2folderOID(sharedFolder), child = OID.generate();
        Map<DID, Long> emptyVersion = Maps.newHashMap(), version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "shared_folder", null, version, null)),
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder", null, emptyVersion, null)),
                new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, null))));

        submitBatchSuccessfully(rootStore, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(3L));
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getOid(), equalTo(sharedFolder));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);
        assertThat(t.transforms.get(1).getOid(), equalTo(folder));
        assertThat(t.transforms.get(1).getTransformType(), equalTo(TransformType.SHARE));

        sharedFolder = SID.generate();
        folder = SID.convertedStoreSID2folderOID(sharedFolder);
        child = OID.generate();
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "other_name", null, version, null)),
                // detection should not rely on a name conflict ideally, this will cause folder to be renamed and then SHARE'd
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder2", null, emptyVersion, null)),
                new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(7L));
        assertThat(t.transforms.size(), equalTo(1));
        assertThat(t.transforms.get(0).getOid(), equalTo(sharedFolder));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);
        assertThat(t.transforms.get(3).getOid(), equalTo(rootStore));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.RENAME_CHILD));
        assertThat(t.transforms.get(3).getChild(), equalTo(folder));
        assertThat(new String(t.transforms.get(3).getChildName()), equalTo("shared_folder2"));

        assertThat(t.transforms.get(4).getOid(), equalTo(folder));
        assertThat(t.transforms.get(4).getTransformType(), equalTo(TransformType.SHARE));
    }

    @Test
    public void objectsShouldBePersistedAcrossFolderSharing() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), sharedFolder = SID.generate();
        OID folder = SID.convertedStoreSID2folderOID(sharedFolder), child = OID.generate();
        Map<DID, Long> emptyVersion = Maps.newHashMap(), version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        List<TransformBatchOperation> ops = Lists.newArrayList(
            new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "shared_folder", null, version, null)),
            new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, null)),
            new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder", null, emptyVersion, null)),
            new TransformBatchOperation(child, new UpdateContent(0L, hash, 100L, 1024L, version)));

        submitOpsSuccessFullyWithin(rootStore, ops, 10);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(5L));
        assertThat(t.transforms.size(), equalTo(2));
        assertThat(t.transforms.get(0).getOid(), equalTo(sharedFolder));
        assertThat(t.transforms.get(0).getChild(), equalTo(child));
        assertThat(t.transforms.get(0).getTransformType(), equalTo(TransformType.INSERT_CHILD));

        assertThat(t.transforms.get(1).getOid(), equalTo(child));
        assertThat(t.transforms.get(1).getTransformType(), equalTo(TransformType.UPDATE_CONTENT));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldCarryAliasesOverFolderSharing() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), sharedFolder = SID.generate();
        OID folder = SID.convertedStoreSID2folderOID(sharedFolder), folderAlias = OID.generate(), child = OID.generate(), childAlias = OID.generate(), child2 = OID.generate();
        Map<DID, Long> emptyVersion = Maps.newHashMap(), version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        List<TransformBatchOperation> ops = Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(folder, ObjectType.FOLDER, "shared_folder", null, version, Lists.newArrayList(folderAlias))),
                new TransformBatchOperation(folder, insertChild(child, ObjectType.FILE, "child", null, version, Lists.newArrayList(childAlias))),
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder", null, emptyVersion, null)),
                new TransformBatchOperation(folderAlias, insertChild(child2, ObjectType.FILE, "child2", null, version, null)),
                new TransformBatchOperation(childAlias, new UpdateContent(0L, hash, 100L, 1024L, version)));

        submitOpsSuccessFullyWithin(rootStore, ops, 10);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.transforms.size(), equalTo(3));
        assertThat(t.transforms, containsInAnyOrder(
                TestTransformsResource.matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.INSERT_CHILD, child, ObjectType.FILE, "child"),
                TestTransformsResource.matchesReorderableMetaTransform(DEVICE, sharedFolder, TransformType.INSERT_CHILD, child2, ObjectType.FILE, "child2"),
                TestTransformsResource.matchesReorderableContentTransform(DEVICE, child, 1L, hash, 100L, 1024L)
        ));
    }

    @Test
    public void shouldDropFolderIfOIDDoesntMatchSID() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), sharedFolder = SID.generate();
        OID folder = SID.convertedStoreSID2folderOID(sharedFolder), folderAlias = OID.generate(), child = OID.generate();
        Map<DID, Long> emptyVersion = Maps.newHashMap(), version = Maps.newHashMap();
        DID device = DID.generate();
        version.put(device, 1L);

        List<TransformBatchOperation> ops = Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(folderAlias, ObjectType.FOLDER, "shared_folder", null, version, Lists.newArrayList(folder))),
                new TransformBatchOperation(folderAlias, insertChild(child, ObjectType.FILE, "child", null, version, null)),
                new TransformBatchOperation(rootStore, insertChild(sharedFolder, ObjectType.STORE, "shared_folder", null, emptyVersion, null)));

        submitOpsSuccessFullyWithin(rootStore, ops, 10);
        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);

        assertThat(t.maxTransformCount, equalTo(4L));
        assertThat(t.transforms.get(2).getOid(), equalTo(rootStore));
        assertThat(t.transforms.get(2).getChild(), equalTo(folderAlias));
        assertThat(t.transforms.get(2).getTransformType(), equalTo(TransformType.REMOVE_CHILD));
        assertThat(t.transforms.get(3).getOid(), equalTo(rootStore));
        assertThat(t.transforms.get(3).getChild(), equalTo(sharedFolder));
        assertThat(t.transforms.get(3).getTransformType(), equalTo(TransformType.INSERT_CHILD));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.transforms.size(), equalTo(0));

        OID child2 = OID.generate(), child3 = OID.generate();
        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(folderAlias, insertChild(child2, ObjectType.FILE, "child2", null, version, null)),
                new TransformBatchOperation(folderAlias, insertChild(child3, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(child3, insertChild(child2, ObjectType.FOLDER, "nested_child", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.transforms.size(), equalTo(0));

        // replicates what is in the old folder
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(sharedFolder, insertChild(child, ObjectType.FILE, "child", null, version, null)),
                new TransformBatchOperation(sharedFolder, insertChild(child2, ObjectType.FILE, "child2", null, version, null)),
                new TransformBatchOperation(sharedFolder, insertChild(child3, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(child3, insertChild(child2, ObjectType.FILE, "nested_child", null, version, null))));
        submitBatchSuccessfully(sharedFolder, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, sharedFolder, 0L, 10);
        assertThat(t.transforms.size(), equalTo(4));
        assertThat(t.transforms.get(0).getChild(), not(child));
        assertThat(t.transforms.get(1).getChild(), not(child2));
        assertThat(t.transforms.get(2).getChild(), not(child3));
        assertThat(t.transforms.get(3).getChild(), not(child2));
    }

    @Test
    public void canHandleInterleavedRemoveChild() throws Exception
    {
        SID store = SID.generate();
        OID child = OID.generate(), child2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        version.put(DID.generate(), 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(child, insertChild(child2, ObjectType.FILE, "file", null, version, null))));
        submitBatchSuccessfully(store, batch);

        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, child, child2);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(3L));

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child2, new UpdateContent(0L, hash, 100L, 1024L, version)),
                new TransformBatchOperation(store, insertChild(child2, ObjectType.FILE, "file", null, version, null))));
        submitBatchSuccessfully(store, batch);

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.maxTransformCount, equalTo(3L));
    }

    @Test
    public void shouldShareFolderIfAnchorIsDeleted() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), store = SID.generate();
        OID anchor = SID.convertedStoreSID2folderOID(store), child = OID.generate(), child2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), emptyVersion = Maps.newHashMap();
        version.put(DID.generate(), 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(anchor, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(anchor, insertChild(child, ObjectType.FILE, "file", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, rootStore, anchor);
        // cause a name conflict
        PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder");
        PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "shared folder");

        List<TransformBatchOperation> ops = Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(store, ObjectType.STORE, "shared folder", null, emptyVersion, null)),
                new TransformBatchOperation(anchor, insertChild(child2, ObjectType.FILE, "file2", null, version, null)));
        submitOpsSuccessFullyWithin(rootStore, ops, 10);

        byte[] hash = new byte[32], hash2 = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        random.nextBytes(hash2);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100L, 1024L, version)),
                new TransformBatchOperation(child2, new UpdateContent(0L, hash2, 200L, 512L, version))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);
        assertThat(t.transforms.size(), equalTo(10));
        assertThat(t.transforms.get(9).getTransformType(), equalTo(TransformType.SHARE));
        assertThat(t.transforms.get(9).getOid(), equalTo(anchor));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.transforms.size(), equalTo(4));
    }

    @Test
    public void shouldShareFolderIfAnchorIsMigrated() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), store = SID.generate(), store2 = SID.generate();
        OID anchor = SID.convertedStoreSID2folderOID(store), child = OID.generate(), child2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), emptyVersion = Maps.newHashMap();
        version.put(DID.generate(), 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(anchor, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(anchor, insertChild(child, ObjectType.FILE, "file", null, version, null))));
        submitBatchSuccessfully(rootStore, batch);

        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.moveFileOrFolder(AUTHENTICATED, rootStore, store2, anchor, "migrated").jobID, 10);
        // cause a name conflict
        PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "folder");
        PolarisHelpers.newFolder(AUTHENTICATED, rootStore, "shared folder");

        List<TransformBatchOperation> ops = Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(store, ObjectType.STORE, "shared folder", null, emptyVersion, null)),
                new TransformBatchOperation(anchor, insertChild(child2, ObjectType.FILE, "file2", null, version, null)));
        submitOpsSuccessFullyWithin(rootStore, ops, 10);

        byte[] hash = new byte[32], hash2 = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        random.nextBytes(hash2);
        batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(child, new UpdateContent(0L, hash, 100L, 1024L, version)),
                new TransformBatchOperation(child2, new UpdateContent(0L, hash2, 200L, 512L, version))));
        submitBatchSuccessfully(store, batch);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);
        assertThat(t.transforms.size(), equalTo(10));
        assertThat(t.transforms.get(9).getTransformType(), equalTo(TransformType.SHARE));
        assertThat(t.transforms.get(9).getOid(), equalTo(anchor));

        t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        assertThat(t.transforms.size(), equalTo(4));
    }

    @Test
    public void shouldHandleDeletedAndMigratedAnchorWithoutMatchingOID() throws Exception
    {
        SID rootStore = SID.rootSID(USERID), store = SID.generate(), store2 = SID.generate(), crossStore = SID.generate();
        OID anchor1 = SID.convertedStoreSID2folderOID(store), anchor2 = SID.convertedStoreSID2folderOID(store2);
        OID alias1 = OID.generate(), alias2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap(), emptyVersion = Maps.newHashMap();
        version.put(DID.generate(), 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(alias1, ObjectType.FOLDER, "folder", null, version, Lists.newArrayList(anchor1))),
                new TransformBatchOperation(rootStore, insertChild(alias2, ObjectType.FOLDER, "folder2", null, version, Lists.newArrayList(anchor2)))));
        submitBatchSuccessfully(rootStore, batch);

        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, rootStore, alias1);
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.moveFileOrFolder(AUTHENTICATED, rootStore, crossStore, alias2, "migrated").jobID, 10);

        List<TransformBatchOperation> ops = Lists.newArrayList(
                new TransformBatchOperation(rootStore, insertChild(store, ObjectType.STORE, "shared folder", null, emptyVersion, null)),
                new TransformBatchOperation(rootStore, insertChild(store2, ObjectType.STORE, "shared folder2", null, emptyVersion, null)));
        submitOpsSuccessFullyWithin(rootStore, ops, 10);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, rootStore, 0L, 10);
        assertThat(t.transforms.size(), equalTo(6));
        assertThat(t.transforms.get(4).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(4).getChild(), equalTo(store));
        assertThat(t.transforms.get(5).getTransformType(), equalTo(TransformType.INSERT_CHILD));
        assertThat(t.transforms.get(5).getChild(), equalTo(store2));
    }

    @Test
    public void canHandleInterleavedCrossStoreMove() throws Exception
    {
        SID store = SID.generate(), store2 = SID.generate();
        OID child = OID.generate(), child2 = OID.generate();
        Map<DID, Long> version = Maps.newHashMap();
        version.put(DID.generate(), 1L);

        TransformBatch batch = new TransformBatch(Lists.newArrayList(
                new TransformBatchOperation(store, insertChild(child, ObjectType.FOLDER, "folder", null, version, null)),
                new TransformBatchOperation(child, insertChild(child2, ObjectType.FILE, "file", null, version, null))));
        submitBatchSuccessfully(store, batch);

        PolarisHelpers.moveObject(AUTHENTICATED, store, store2, child, "folder");

        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);
        List<TransformBatchOperation> ops = Lists.newArrayList(
            new TransformBatchOperation(child2, new UpdateContent(0L, hash, 100L, 1024L, version)),
            new TransformBatchOperation(store, insertChild(child2, ObjectType.FILE, "file", null, version, null)));
        submitOpsSuccessFullyWithin(store, ops, 10);

        Transforms t = PolarisHelpers.getTransforms(AUTHENTICATED, store, 0L, 10);
        // 2 inserts and a remove in the original store, then 2 inserts in the new store
        assertThat(t.maxTransformCount, equalTo(5L));
    }

    // will fail only if fails "tries" number of times in a row without getting any work done
    private void submitOpsSuccessFullyWithin(UniqueID store, List<TransformBatchOperation> ops, int tries) throws InterruptedException {
        int failures = 0;
        while (true) {
            try {
                TransformBatchResult r = submitBatch(store, new TransformBatch(ops));
                for (TransformBatchOperationResult opResult : r.results) {
                    if (opResult.successful) {
                        ops.remove(0);
                        failures = 0;
                    } else {
                        failures++;
                        break;
                    }
                }
                if (ops.isEmpty()) {
                    // successfully submitted all ops
                    return;
                }
            } catch (Exception e) {
                failures++;
            }
            if (failures > tries) {
                throw new AssertionError(String.format("failed to submit batch over %d tries", tries));
            } else {
                Thread.sleep(250);
            }
        }
    }

    private void submitBatchSuccessfully(UniqueID store, TransformBatch batch)
    {
        TransformBatchResult r = submitBatch(store, batch);
        for (TransformBatchOperationResult opResult : r.results) {
            assertThat("failed a batch operation", opResult.successful);
        }
    }

    private TransformBatchResult submitBatch(UniqueID store, TransformBatch batch)
    {
        return postConversionBatch(store, batch)
                .assertThat().statusCode(SC_OK)
                .and()
                .extract().response().as(TransformBatchResult.class);
    }

    private InsertChild insertChild(UniqueID c, ObjectType t, String n, @Nullable UniqueID m, Map<DID, Long> v, @Nullable List<UniqueID> a)
    {
        return new InsertChild(c, t, PolarisUtilities.stringToUTF8Bytes(n), m, v, a == null ? Lists.newArrayList() : a);
    }

    private ValidatableResponse postConversionBatch(UniqueID store, TransformBatch batch)
    {
        return given()
                .spec(AUTHENTICATED)
                .and()
                .header(CONTENT_TYPE, APPLICATION_JSON).and().body(batch)
                .and()
                .when().post(PolarisTestServer.getConversionURL(store))
                .then();
    }
}