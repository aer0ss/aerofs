package com.aerofs.daemon.rest;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.rest.util.RestObject;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;

public class TestFolderResource extends AbstractRestTest
{
    private final String RESOURCE = "/v0.9/folders/{folder}";

    public TestFolderResource(boolean useProxy)
    {
        super(useProxy);
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldGetMetadata() throws Exception
    {
        mds.root().dir("d0").file("f1");

        givenAcces()
        .expect()
                .statusCode(200)
                .body("id", equalTo(object("d0").toStringFormal()))
                .body("name", equalTo("d0"))
                .body("is_shared", equalTo(false))
        .when().get(RESOURCE, object("d0").toStringFormal());
    }

    @Test
    public void shouldReturn404ForFile() throws Exception
    {
        mds.root().file("f1");

        givenAcces()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, object("f1").toStringFormal());
    }
}
