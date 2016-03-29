package com.aerofs.sp.sparta.resources;

import com.aerofs.ids.DID;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static org.hamcrest.Matchers.*;

public class TestDevicesResource extends AbstractResourceTest {
    private final String RESOURCE_BASE = "/v1.3/devices";
    private final String RESOURCE = RESOURCE_BASE + "/{did}";
    private final String COUNT_RESOURCE = "/v1.4/devices/count";

    private final DID did = DID.generate();

    @Test
    public void shouldReturn401WhenTokenMissing() throws Exception
    {
        expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when()
                .get(RESOURCE, did.toStringFormal());
    }

    @Test
    public void shouldGetOwnDevice() throws Exception
    {
        mkDevice(did, user, "My Shiny Device", "Windows", "windows 3.1");

        givenReadAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(did.toStringFormal()))
                .body("owner", equalTo(user.getString()))
                .body("name", equalTo("My Shiny Device"))
                .body("os_family", equalTo("Windows"))
                .body("install_date", isValidDate())
        .when().log().everything()
                .get(RESOURCE, did.toStringFormal());
    }

    @Test
    public void shouldGetOtherUserDeviceWhenAdmin() throws Exception
    {
        mkDevice(did, user, "My Shiny Device", "Windows", "windows 3.1");

        givenAdminAccess()
        .expect()
                .statusCode(200)
                .body("id", equalTo(did.toStringFormal()))
                .body("owner", equalTo(user.getString()))
                .body("name", equalTo("My Shiny Device"))
                .body("os_family", equalTo("Windows"))
                .body("install_date", isValidDate())
        .when().log().everything()
                .get(RESOURCE, did.toStringFormal());
    }

    @Test
    public void shouldReturn404WhenGetByOther() throws Exception
    {
        givenOtherAccess()
        .expect()
                .statusCode(404)
                .body("type", equalTo("NOT_FOUND"))
        .when().log().everything()
                .get(RESOURCE, did.toStringFormal());
    }

    @Test
    public void count_shouldReturn401ForNonPrivilegedServiceToken() throws Exception
    {
        givenAdminAccess()
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(COUNT_RESOURCE);

        givenAdminAccess()
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(COUNT_RESOURCE);

        givenAccess("thisisnotavalidtoken")
        .expect()
                .statusCode(401)
                .body("type", equalTo("UNAUTHORIZED"))
        .when().log().everything()
                .get(COUNT_RESOURCE);
    }

    @Test
    public void count_shouldReturn200ForValidPrivilegedServiceToken() throws Exception
    {
        givenSecret("analytics", deploymentSecret)
        .expect()
                .statusCode(200)
        .when().log().everything()
                .get(COUNT_RESOURCE + "?device_os=Windows");
    }

    @Test
    public void count_shouldReturn400WhenRequestHasInvalidQueryParams() throws Exception
    {
        givenSecret("analytics", deploymentSecret)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().log().everything()
                .get(COUNT_RESOURCE);

        givenSecret("analytics", deploymentSecret)
        .expect()
                .statusCode(400)
                .body("type", equalTo("BAD_ARGS"))
        .when().log().everything()
                .get(COUNT_RESOURCE + "?device_os=totallynotavalidos");
    }

    @Test
    public void count_shouldReturn200WhenRequestHasValidQueryParam() throws Exception
    {
        givenSecret("analytics", deploymentSecret)
        .expect()
                .statusCode(200)
        .when().log().everything()
                .get(COUNT_RESOURCE + "?device_os=Mac OS X");
    }

    // TODO: test the /status route (requires more advanced mocking of vk)
}
