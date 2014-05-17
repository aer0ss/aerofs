package com.aerofs.daemon.rest;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSAnchor;
import com.aerofs.base.id.RestObject;
import org.junit.Test;

import java.util.Date;

import static org.hamcrest.Matchers.*;

public class TestChildrenResource extends AbstractRestTest
{
    private final String RESOURCE = "/v0.9/children/{folder}";

    public TestChildrenResource(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldListRoot() throws Exception
    {
        mds.root()
                .dir("d").parent()
                .file("f").parent()
                .anchor("a");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(object("").toStringFormal()))
                .body("files", hasSize(1)).body("files.name", hasItems("f"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("d", "a"))
        .when().get(RESOURCE, "");
    }

    @Test
    public void shouldListEmptyFolder() throws Exception
    {
        mds.root()
                .dir("d");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(object("d").toStringFormal()))
                .body("files", empty())
                .body("folders", empty())
        .when().get(RESOURCE, object("d").toStringFormal());
    }

    @Test
    public void shouldListAnchorAsAnchor() throws Exception
    {
        MockDSAnchor a = mds.root().anchor("a");
        a.dir("d").parent()
                .file("f");

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(object("").toStringFormal()))
                .body("files", empty())
                .body("folders", hasSize(1))
                .body("folders.name", hasItem("a"))
                .body("folders.id", hasItem(new RestObject(rootSID, a.soid().oid()).toStringFormal()))
                .body("folders.is_shared", hasItem(true))
        .when().get(RESOURCE, "");
    }

    @Test
    public void shouldFollowAnchor() throws Exception
    {
        MockDSAnchor a = mds.root().anchor("a");
        a.dir("d").parent()
                .file("f").caMaster(42, 0xdeadbeef);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1))
                .body("files[0].name", equalTo("f"))
                .body("files[0].id", equalTo(object("a/f").toStringFormal()))
                .body("files[0].last_modified", equalTo(ISO_8601.format(new Date(0xdeadbeef))))
                .body("files[0].size", equalTo(42))
                .body("files[0].mime_type", equalTo("application/octet-stream"))
                .body("folders", hasSize(1))
                .body("folders[0].name", equalTo("d"))
                .body("folders[0].id", equalTo(object("a/d").toStringFormal()))
                .body("folders[0].is_shared", equalTo(false))
        .when().get(RESOURCE, new RestObject(rootSID, a.soid().oid()).toStringFormal());
    }
}
