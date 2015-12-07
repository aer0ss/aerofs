package com.aerofs.polaris.external_api.rest;

import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.polaris.PolarisHelpers;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import static com.aerofs.polaris.PolarisTestServer.getApiChildrenURL;
import static org.hamcrest.Matchers.*;

public class TestChildrenResource extends AbstractRestTest
{
    @Test
    public void shouldReturn400ForInvalidId() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().get(getApiChildrenURL() +  "bla");
    }

    @Test
    public void shouldReturn404ForNonExistingStore() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiChildrenURL() + new RestObject(SID.generate(), OID.generate()).toStringFormal());
    }

    @Test
    public void shouldReturn404ForNonExistingDir() throws Exception
    {
        givenAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().get(getApiChildrenURL() + new RestObject(rootSID, OID.generate()).toStringFormal());
    }

    @Test
    public void shouldListRoot() throws Exception
    {
        PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        OID share1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "share1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, share1).jobID, 5);
        String restObject = rootSID.toStringFormal() + OID.ROOT.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(restObject))
                .body("files", hasSize(1)).body("files.name", hasItems("file1"))
                .body("folders", hasSize(2)).body("folders.name", hasItems("folder1", "share1"))
        .when().get(getApiChildrenURL() + restObject);
    }

    @Test
    public void shouldListEmptyFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        String restObject = rootSID.toStringFormal() + folder1.toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(restObject))
                .body("files", empty())
                .body("folders", empty())
        .when().get(getApiChildrenURL() + restObject);
    }

    @Test
    public void shouldListAnchorAsAnchor() throws Exception
    {
        OID share1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "share1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, share1).jobID, 5);
        String restObject = rootSID.toStringFormal() + SID.folderOID2convertedAnchorOID(share1).toStringFormal();

        givenAccess()
        .expect()
                .statusCode(200)
                .body("parent", equalTo(new RestObject(rootSID, OID.ROOT).toStringFormal()))
                .body("files", empty())
                .body("folders", hasSize(1))
                .body("folders.name", hasItem("share1"))
                .body("folders.id", hasItem(restObject))
                .body("folders.is_shared", hasItem(true))
        .when().get(getApiChildrenURL() + "root");
    }

    @Test
    public void shouldFollowAnchor() throws Exception
    {
        OID share1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "share1");
        PolarisHelpers.waitForJobCompletion(AUTHENTICATED, PolarisHelpers.shareFolder(AUTHENTICATED, rootSID, share1).jobID, 5);
        SID sid1 = SID.folderOID2convertedStoreSID(share1);
        String restObject = rootSID.toStringFormal() + sid1.toStringFormal();
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, sid1, "folder1");
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, sid1, "file1");

        byte[] initialHash = new byte[32];
        Random random = new Random();
        random.nextBytes(initialHash);
        PolarisHelpers.newFileContent(AUTHENTICATED, file1, 0, initialHash, 1, 100);

        givenAccess()
        .expect()
                .statusCode(200)
                .body("files", hasSize(1))
                .body("files[0].name", equalTo("file1"))
                .body("files[0].id", equalTo(new RestObject(sid1, file1).toStringFormal()))
                .body("files[0].size", equalTo(1))
                .body("files[0].last_modified", equalTo(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        .format(new Date(100))))
                .body("files[0].mime_type", equalTo("application/octet-stream"))
                .body("folders", hasSize(1))
                .body("folders[0].name", equalTo("folder1"))
                .body("folders[0].id", equalTo(new RestObject(sid1, folder1).toStringFormal()))
                .body("folders[0].is_shared", equalTo(false))
        .when().get(getApiChildrenURL() + restObject);
    }
}
