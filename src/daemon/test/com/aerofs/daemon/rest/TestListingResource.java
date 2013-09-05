package com.aerofs.daemon.rest;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import org.junit.Test;
import org.testng.Assert;

import java.sql.SQLException;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class TestListingResource extends AbstractRestTest
{
    private final String RESOURCE = "/0/users/" + user +"/list/{folder}";

    RestObject object(String path) throws SQLException
    {
        SOID soid = ds.resolveNullable_(Path.fromString(rootSID, path));
        Assert.assertNotNull(soid, path);
        SID sid = sm.get_(soid.sidx());
        return new RestObject(sid, soid.oid());
    }

    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(RESOURCE, "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(RESOURCE, new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        expect()
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

        expect()
                .statusCode(200)
                .body("files", hasSize(1)).body("files.name", hasItems("f"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("d", "a"))
            .when().get(RESOURCE, "@root");
    }

    @Test
    public void shouldListEmptyFolder() throws Exception
    {
        mds.root()
                .dir("d");

        expect()
                .statusCode(200)
                .body("files", empty())
                .body("folders", empty())
        .when().get(RESOURCE, object("d").toStringFormal());
    }
}
