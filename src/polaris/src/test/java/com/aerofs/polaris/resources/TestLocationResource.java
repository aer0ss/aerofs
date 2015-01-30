package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.PolarisHelpers;
import com.aerofs.polaris.PolarisTestServer;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

@SuppressWarnings("unchecked")
public final class TestLocationResource {

    static {
        RestAssured.config = PolarisHelpers.newRestAssuredConfig();
    }

    private static final String USERID = "test@aerofs.com";
    private static final String DEVICE = Identifiers.newRandomDevice();
    private static final RequestSpecification AUTHENTICATED = PolarisHelpers.newAuthedAeroUserReqSpec(USERID, DEVICE);

    private final MySQLDatabase database = new MySQLDatabase("test");
    private final PolarisTestServer polaris = new PolarisTestServer();

    @Rule
    public RuleChain chain = RuleChain.outerRule(database).around(polaris);

    @Test
    public void shouldAddLocationsForLogicalObject() {
        // create a root folder and a file under it
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, DEVICE);

        // verify that this location exists
        List<String> locations = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations, contains(DEVICE));
    }

    @Test
    public void shouldFailToAddLocationForNonExistentLogicalObject() {
        String fake = Identifiers.newRandomObject(); // fake object -- doesn't exist

        given()
                .spec(AUTHENTICATED)
                .and()
                .when().post(PolarisTestServer.getLocationURL(fake, (long) 0, DEVICE))
                .then()
                .assertThat().statusCode(SC_NOT_FOUND);
    }

    @Test
    public void shouldStillBeAbleToAddLocationForDeletedObject() {
        // create a file under the root folder
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // add a location for this file
        String dev0 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev0);

        // delete the file
        PolarisHelpers.removeFileOrFolder(AUTHENTICATED, root, file);

        // should still be able to add a location for this file
        String dev1 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev1);

        // when we get a list of locations for this object
        List<String> locations = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations, containsInAnyOrder(dev0, dev1));
    }

    @Test
    public void shouldGetLocationsForLogicalObject() {
        // create a root folder and a file under it
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // indicate that this object is available at multiple locations
        String dev0 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev0);
        String dev1 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev1);
        String dev2 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev2);

        // verify that we have stored this info
        List<String> locations = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations, containsInAnyOrder(dev0, dev1, dev2));
    }

    @Test
    public void shouldFailToGetLocationsForNonExistentObject() {
        String fake = Identifiers.newRandomObject();

        // shouldn't be able to get locations for this fake object
        given()
                .spec(AUTHENTICATED)
                .and()
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .and()
                .when().get(PolarisTestServer.getLocationsURL(fake, (long) 0))
                .then()
                .assertThat().statusCode(SC_NOT_FOUND)
                .and();
    }

    @Test
    public void shouldDeleteLocationsForLogicalObject() {
        // create a root folder and a file under it
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");

        // indicate that this object is available at multiple locations
        String dev0 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev0);
        String dev1 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev1);
        String dev2 = Identifiers.newRandomDevice();
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, dev2);

        // verify that we have stored this info
        List<String> locations0 = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations0, containsInAnyOrder(dev0, dev1, dev2));

        // now, indicate that dev1 no longer has that object
        PolarisHelpers.removeLocation(AUTHENTICATED, file, 0, dev1);

        // verify that this list is now updated and no longer contains the removed device
        List<String> locations1 = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations1, containsInAnyOrder(dev0, dev2));
    }

    // interesting - we don't have to say the content info to say that the object is available

    @Test
    public void shouldNoopWhenAttemptingToDeleteNonExistingDeviceForLogicalObject() {
        // create a root folder and a file under it and say that the object is available at this device
        String root = Identifiers.newRandomSharedFolder();
        String file = PolarisHelpers.newFile(AUTHENTICATED, root, "file");
        PolarisHelpers.addLocation(AUTHENTICATED, file, 0, DEVICE);

        // verify that we have stored this info
        List<String> locations0 = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations0, containsInAnyOrder(DEVICE));

        // now, indicate that we no longer have this object
        PolarisHelpers.removeLocation(AUTHENTICATED, file, 0, DEVICE);

        // verify that this list is now updated and no longer contains the removed device
        List<String> locations1 = PolarisHelpers.getLocations(AUTHENTICATED, file, 0).extract().response().as(List.class);
        assertThat(locations1, empty());

        // attempt to delete ourselves again
        PolarisHelpers.removeLocation(AUTHENTICATED, file, 0, DEVICE);

        // and again!
        PolarisHelpers.removeLocation(AUTHENTICATED, file, 0, DEVICE);

        // ^^^ neither of the above should fail
    }
}