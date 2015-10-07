package com.aerofs.polaris.external_api.rest;

import com.aerofs.base.id.RestObject;
import com.aerofs.ids.OID;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;

public final class TestAuth extends AbstractRestTest
{

   @Test
    public void shouldReturn403WhenAccessTokenMissing() throws Exception
    {
        OID file = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file");
        RestObject object = new RestObject(rootSID, file);

        expect()
                .statusCode(403)
                .when().get(PolarisTestServer.getApiFilesURL() + object.toStringFormal());
    }

    @Test
    public void shouldReturn401WhenAccessTokenInvalid() throws Exception
    {
        OID file = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file");
        RestObject object = new RestObject(rootSID, file);

        givenInvalidToken()
        .expect()
                .statusCode(401)
        .when().get(PolarisTestServer.getApiFilesURL() + object.toStringFormal());
    }

    @Test
    public void shouldReturn401WhenAccessTokenExpired() throws Exception
    {
        OID file = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file");
        RestObject object = new RestObject(rootSID, file);

        givenExpiredToken()
        .expect()
                .statusCode(401)
        .when().get(PolarisTestServer.getApiFilesURL() + object.toStringFormal());
    }

    @Test
    public void shouldGetFileWithTokenScopedToFile() throws Exception
    {
        OID file = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file");
        RestObject object = new RestObject(rootSID, file);

        givenReadAccessTo(object)
        .expect()
                .statusCode(200)
        .when().get(PolarisTestServer.getApiFilesURL() + object.toStringFormal());
    }

    @Test
    public void shouldReturn403WithTokenScopedToDifferentFile() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        OID file2 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file2");
        RestObject objectFile1 = new RestObject(rootSID, file1);
        RestObject objectFile2 = new RestObject(rootSID, file2);

        givenReadAccessTo(objectFile1)
        .expect()
                .statusCode(403)
        .when().get(PolarisTestServer.getApiFilesURL() + objectFile2.toStringFormal());
    }

    @Test
    public void shouldGetFileWithTokenScopedToParent() throws Exception
    {
        OID file1 = PolarisHelpers.newFile(AUTHENTICATED, rootSID, "file1");
        RestObject objectFile1 = new RestObject(rootSID, file1);

        givenReadAccessTo(new RestObject(rootSID, OID.ROOT))
        .expect()
                .statusCode(200)
        .when().get(PolarisTestServer.getApiFilesURL() + objectFile1.toStringFormal());
    }

    @Test
    public void shouldGetFolderWithTokenScopedToParentFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        RestObject objectFolder2 = new RestObject(rootSID, folder2);

        givenReadAccessTo(new RestObject(rootSID, folder1))
        .expect()
                .statusCode(200)
        .when().get(PolarisTestServer.getApiFoldersURL() + objectFolder2.toStringFormal());
    }

    @Test
    public void shouldGet403WhenTokenScopedToChildFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        OID folder2 = PolarisHelpers.newFolder(AUTHENTICATED, folder1, "folder2");
        RestObject objectFolder1 = new RestObject(rootSID, folder1);

        givenReadAccessTo(new RestObject(rootSID, folder2))
        .expect()
                .statusCode(403)
        .when().get(PolarisTestServer.getApiFoldersURL() + objectFolder1.toStringFormal());
    }

    @Test
    public void shouldGetFolderWithTokenScopedToFolder() throws Exception
    {
        OID folder1 = PolarisHelpers.newFolder(AUTHENTICATED, rootSID, "folder1");
        RestObject objectFolder1 = new RestObject(rootSID, folder1);

        givenReadAccessTo(objectFolder1)
        .expect()
                .statusCode(200)
        .when().get(PolarisTestServer.getApiFoldersURL() + objectFolder1.toStringFormal());
    }

    @Test
    public void shouldGetAppDataFolder() throws Exception
    {
        givenTokenWithScopes(ImmutableSet.of("files.appdata"))
        .expect()
                .statusCode(200)
        .when().get(PolarisTestServer.getApiFoldersURL() + "appdata");
    }
}
