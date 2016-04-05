package com.aerofs.polaris.logical;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.aerofs.polaris.api.operation.Transforms;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.aerofs.polaris.ssmp.SSMPListener;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPIdentifier;
import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static com.aerofs.polaris.resources.TestTransformsResource.matchesMetaTransform;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class TestSFAutoJoinAndLeave {
    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    public static MySQLDatabase database = new MySQLDatabase("test");
    public static PolarisTestServer polaris = new PolarisTestServer();

    private static final UserID user = UserID.fromInternal("test@aerofs.com");
    private static final SID userRoot = SID.rootSID(user);
    private static final DID device = DID.generate();
    private final RequestSpecification verified = PolarisHelpers.newAuthedAeroUserReqSpec(user, device);
    private final SID store = SID.generate();

    private SSMPListener ssmpListener;

    @ClassRule
    public static RuleChain rule = RuleChain.outerRule(database).around(polaris);

    @Before
    public void beforeTest() throws Exception
    {
        ssmpListener = polaris.getPolarisEnvironment().getInstance(SSMPListener.class);
        doReturn("shared folder").when(polaris.getStoreNames()).getStoreDefaultName(any(), eq(store));
    }

    @After
    public void afterTest() throws Exception {
        polaris.resetMocks();
        database.clear();
    }

    @Test
    public void shouldInsertStore() throws Exception
    {
        ssmpListener.eventReceived(userJoined(user, store));

        Transforms applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(1L));
        Transform transform = applied.transforms.get(0);
        assertThat(transform, matchesMetaTransform(1, DID.DUMMY, userRoot, TransformType.INSERT_CHILD, 1, SID.storeSID2anchorOID(store), ObjectType.STORE, "shared folder", null));
    }

    @Test
    public void shouldRemoveStore() throws Exception
    {
        PolarisHelpers.newObject(verified, userRoot, store, "shared folder", ObjectType.STORE);

        ssmpListener.eventReceived(userLeft(user, store));

        Transforms applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));
        Transform transform = applied.transforms.get(1);
        assertThat(transform, matchesMetaTransform(2, DID.DUMMY, userRoot, TransformType.REMOVE_CHILD, 2, SID.storeSID2anchorOID(store), null, null, null));
    }

    @Test
    public void shouldNotifyAccessManager() throws Exception
    {
        ssmpListener.eventReceived(userChanged(user, store));
        verify(polaris.getAccessManager()).accessChanged(user, store);

        ssmpListener.eventReceived(userLeft(user, store));
        verify(polaris.getAccessManager(), times(2)).accessChanged(user, store);
    }

    @Test
    public void shouldNotInsertStoreIfAlreadyExists() throws Exception
    {
        PolarisHelpers.newObject(verified, userRoot, store, "shared folder", ObjectType.STORE).assertThat().statusCode(HttpStatus.SC_OK);

        ssmpListener.eventReceived(userJoined(user, store));

        Transforms applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(1));
        assertThat(applied.maxTransformCount, is(1L));
        Transform transform = applied.transforms.get(0);
        assertThat(transform, matchesMetaTransform(1, device, userRoot, TransformType.INSERT_CHILD, 1, SID.storeSID2anchorOID(store), ObjectType.STORE, "shared folder", null));
    }

    @Test
    public void shouldNotRemoveStoreIfAlreadyRemoved() throws Exception
    {
        ssmpListener.eventReceived(userLeft(user, store));
        Transforms applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(0));
        assertThat(applied.maxTransformCount, is(0L));

        PolarisHelpers.newObject(verified, userRoot, store, "shared folder", ObjectType.STORE).assertThat().statusCode(HttpStatus.SC_OK);
        PolarisHelpers.removeFileOrFolder(verified, userRoot, SID.storeSID2anchorOID(store));

        applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));

        ssmpListener.eventReceived(userLeft(user, store));
        // no new transforms
        applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));
    }

    @Test
    public void shouldRenameStoreIfInsertionCausesANameConflict() throws Exception
    {
        PolarisHelpers.newFolder(verified, userRoot, "shared folder");

        ssmpListener.eventReceived(userJoined(user, store));

        Transforms applied = PolarisHelpers.getTransforms(verified, userRoot, -1, 10);
        assertThat(applied.transforms, hasSize(2));
        assertThat(applied.maxTransformCount, is(2L));
        Transform transform = applied.transforms.get(1);
        assertThat(transform, matchesMetaTransform(2, DID.DUMMY, userRoot, TransformType.INSERT_CHILD, 2, SID.storeSID2anchorOID(store), ObjectType.STORE, "shared folder (2)", null));

        verify(polaris.getStoreNames()).setPersonalStoreName(any(), eq(store), eq("shared folder (2)"));
    }


    private SSMPEvent userJoined(UserID user, SID store) {
        return getSSMPEvent(user, store, Constants.SFNOTIF_JOIN);
    }

    private SSMPEvent userLeft(UserID user, SID store) {
        return getSSMPEvent(user, store, Constants.SFNOTIF_LEAVE);
    }

    private SSMPEvent userChanged(UserID user, SID store) {
        return getSSMPEvent(user, store, Constants.SFNOTIF_CHANGE);
    }

    private SSMPEvent getSSMPEvent(UserID user, SID store, String msg) {
        String b = String.format("%s %s %s %s", Constants.SFNOTIF_PREFIX, store.toStringFormal(), user.getString(), msg);
        byte[] body = b.getBytes(Charsets.UTF_8);
        return new SSMPEvent(SSMPIdentifier.ANONYMOUS, SSMPEvent.Type.UCAST, SSMPIdentifier.fromInternal("polaris"), body);
    }
}
