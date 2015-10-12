package com.aerofs.polaris.external_api.logical;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.base.id.RestObject;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ids.*;
import com.aerofs.oauth.OAuthScopeParsingUtil;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.DeletableChild;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.types.*;
import com.aerofs.polaris.external_api.metadata.MetadataBuilder;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.Migrator;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.Notifier;
import com.aerofs.rest.api.*;
import com.aerofs.rest.util.MimeTypeDetector;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.*;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static com.aerofs.polaris.PolarisHelpers.newFile;
import static com.aerofs.polaris.PolarisHelpers.newFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestMetadataBuilder
{

    private static final UserID USERID = UserID.fromInternal("test@aerofs.com");
    private static final DID DEVICE = DID.generate();
    private static final SID rootStore = SID.rootSID(USERID);

    @ClassRule
    public static MySQLDatabase database = new MySQLDatabase("test");
    private static BasicDataSource dataSource;
    private static DBI realdbi;
    private DBI dbi;

    private ObjectStore objects;
    private AeroOAuthPrincipal principal;
    private MetadataBuilder metadataBuilder;

    @BeforeClass
    public static void setupDB() throws Exception
    {
        // setup database
        PolarisConfiguration configuration = Configuration.loadYAMLConfigurationFromResources(Polaris.class, "polaris_test_server.yml");
        DatabaseConfiguration database = configuration.getDatabase();
        dataSource = (BasicDataSource) Databases.newDataSource(database);

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate();

        // setup JDBI
        DBI dbi = Databases.newDBI(dataSource);
        dbi.registerArgumentFactory(new UniqueIDTypeArgument.UniqueIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new OIDTypeArgument.OIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new SIDTypeArgument.SIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new DIDTypeArgument.DIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());
        dbi.registerArgumentFactory(new JobStatusArgument.JobStatusArgumentFactory());
        realdbi = dbi;
    }

    @AfterClass
    public static void tearDown()
    {
        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Before
    public void setupMocks() throws Exception {
        this.dbi = spy(realdbi);

        this.principal = mock(AeroOAuthPrincipal.class);
        when(principal.getUser()).thenReturn(USERID);
        when(principal.getName()).thenReturn("test");
        when(principal.audience()).thenReturn("audience");
        when(principal.getDID()).thenReturn(DEVICE);
        when(principal.scope()).thenReturn(OAuthScopeParsingUtil.parseScopes(ImmutableSet.of("files.read", "files.write")));

        this.objects = new ObjectStore(mock(AccessManager.class), dbi, mock(Migrator.class));
        this.metadataBuilder = new MetadataBuilder(objects, new MimeTypeDetector(), mock(Notifier.class), dbi);
    }

    @After
    public void clearData()
    {
        database.clear();
    }

    private void verifyCommonMetadata(List<CommonMetadata> commonMetadatas, SID parentSID,
            OID parentOID, List<OID> oids, List<String> names)
    {
        assertEquals(commonMetadatas.size(), oids.size());

        // Verify Rest object ids are same.
        List<String> expectedRestObjects =
                commonMetadatas.stream().map(file -> file.id).collect(Collectors.toList());
        List<String> resultRestObjects =
                oids.stream().map(oid -> new RestObject(parentSID, oid).toStringFormal())
                        .collect(Collectors.toList());

        assertEquals(expectedRestObjects.size(), resultRestObjects.size());
        assertTrue(expectedRestObjects.containsAll(resultRestObjects));

        List<String> expectedFileNames =
                commonMetadatas.stream().map(file -> file.name).collect(Collectors.toList());
        assertTrue(expectedFileNames.containsAll(names));

        List<String> expectedParentRestObjects =
                commonMetadatas.stream().map(file -> file.parent).collect(Collectors.toList());
        List<String> resultParentRestObjects =
                oids.stream().map(oid -> new RestObject(parentSID, parentOID).toStringFormal())
                        .collect(Collectors.toList());
        assertTrue(expectedParentRestObjects.containsAll(resultParentRestObjects));
        commonMetadatas.stream().forEach(file -> assertTrue(file.path == null));
    }

    private void verifyChildFiles(List<File> childFiles, SID parentSID, OID parentOID,
            List<OID> oids, List<String> names)
    {
        List<CommonMetadata> commonMetadatas = childFiles.stream()
                .map(file -> (CommonMetadata)file).collect(Collectors.toList());
        verifyCommonMetadata(commonMetadatas, parentSID, parentOID, oids, names);
        for (File file: childFiles) {
            assertEquals(file.content_state, null);
            assertEquals(file.last_modified, null);
            assertEquals(file.size, null);
            assertEquals(file.mime_type, "application/octet-stream");
        }
    }

    private void verifyChildFolders(List<Folder> childFolders, SID parentSID, OID parentOID,
            List<OID> oids, List<String> names)
    {
        List<CommonMetadata> commonMetadatas = childFolders.stream()
                .map(file -> (CommonMetadata)file).collect(Collectors.toList());
        verifyCommonMetadata(commonMetadatas, parentSID, parentOID, oids, names);
        for (Folder folder: childFolders) {
            assertEquals(folder.is_shared, false);
            assertEquals(folder.sid, null);
            assertEquals(folder.children, null);
        }
    }

    private void verifyFolderCreation(Response response, UniqueID parent, String child)
    {
        assertTrue(response.getEntity() instanceof Folder);
        Folder result = (Folder) response.getEntity();
        assertEquals(result.name, child);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            List<Child> children = objects.children(dao, parent);
            assertEquals(1, children.size());
            assertEquals(child, new String(children.get(0).name));
            return null;
        });
    }

    private void verifyChildDeleted(UniqueID parent, String childName)
    {
        dbi.inTransaction((conn, status) -> {
            Children children = conn.attach(Children.class);
            try (ResultIterator<DeletableChild> c = children.getChildren(parent)) {
                while(c.hasNext()) {
                    DeletableChild child = c.next();
                    if(child.deleted) {
                        assertEquals(childName, new String(child.name));
                    }
                }
            }
            return null;
        });
    }

    @Test
    public void testMetadataForAeroFSRoot() throws Exception
    {
        // Create 2 files and 2 folders under AeroFS root.
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID testFile1 = newFile(rootStore, "testFile1", USERID, DEVICE, objects);
        OID test2 = newFolder(rootStore, "test2", USERID, DEVICE, objects);
        OID testFile2 = newFile(rootStore, "testFile2", USERID, DEVICE, objects);
        Response response =
                metadataBuilder.metadata(principal, RestObject.fromString("root"), "children,path",
                        false);

        assertTrue(response.getEntity() instanceof Folder);
        Folder folder = (Folder)response.getEntity();

        assertEquals(new RestObject(rootStore, OID.ROOT).toStringFormal(), folder.id);
        assertEquals("AeroFS", folder.name);
        assertEquals(new RestObject(rootStore, OID.ROOT).toStringFormal(), folder.parent);
        assertEquals(0, folder.path.folders.size());
        assertEquals(null, folder.sid);

        assertEquals(2, folder.children.files.size());
        assertEquals(2, folder.children.folders.size());

        verifyChildFiles(folder.children.files, rootStore, OID.ROOT,
                Lists.newArrayList(testFile1, testFile2), Lists.newArrayList("testFile1", "testFile2"));

        verifyChildFolders(folder.children.folders, rootStore, OID.ROOT,
                Lists.newArrayList(test1, test2), Lists.newArrayList("test1", "test2"));
    }

    @Test
    public void testMetadataForFolder() throws Exception
    {
        // Create 2 files and 2 folders under a folder.
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID test11 = newFolder(test1, "test11", USERID, DEVICE, objects);
        OID test12 = newFolder(test1, "test12", USERID, DEVICE, objects);
        OID testFile1 = newFile(test1, "testFile1", USERID, DEVICE, objects);
        OID testFile2 = newFile(test1, "testFile2", USERID, DEVICE, objects);

        RestObject object = new RestObject(rootStore, test1);
        Response response =
                metadataBuilder.metadata(principal, object, "children,path",false);

        assertTrue(response.getEntity() instanceof Folder);
        Folder folder = (Folder)response.getEntity();

        assertEquals(object.toStringFormal(), folder.id);
        assertEquals("test1", folder.name);
        assertEquals(new RestObject(rootStore, OID.ROOT).toStringFormal(), folder.parent);

        assertEquals(1, folder.path.folders.size());
        String path = StringUtils.join(folder.path.folders.stream().map(f -> f.name)
                .collect(Collectors.toList()), java.io.File.separator);
        assertEquals("AeroFS", path);

        assertEquals(null, folder.sid);
        assertEquals(2, folder.children.files.size());
        assertEquals(2, folder.children.folders.size());

        verifyChildFiles(folder.children.files, rootStore, test1, Lists.newArrayList(testFile1, testFile2),
                Lists.newArrayList("testFile1", "testFile2"));

        verifyChildFolders(folder.children.folders, rootStore, test1, Lists.newArrayList(test11, test12),
                Lists.newArrayList("test11", "test12"));
    }

    @Test
    public void testMetadataForFile() throws Exception
    {
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID testFile1 = newFile(test1, "testFile1", USERID, DEVICE, objects);
        RestObject object = new RestObject(rootStore, testFile1);

        Response response =
                metadataBuilder.metadata(principal, object, "children,path", true);

        assertTrue(response.getEntity() instanceof File);
        File file = (File)response.getEntity();

        assertEquals(object.toStringFormal(), file.id);
        assertEquals("testFile1", file.name);
        assertEquals(new RestObject(rootStore, test1).toStringFormal(), file.parent);

        assertEquals(2, file.path.folders.size());
        String path =  StringUtils.join(file.path.folders.stream().map(folder -> folder.name)
                        .collect(Collectors.toList()), java.io.File.separator);
        assertEquals("AeroFS/test1", path);

        assertEquals(file.content_state, null);
        assertEquals(file.last_modified, null);
        assertEquals(file.size, null);
        assertEquals(file.mime_type, "application/octet-stream");
    }

    @Test
    public void testPath() throws Exception
    {
        // Create a 5 nested folders and grab the oid of the last one created.
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID test2 = newFolder(test1, "test2", USERID, DEVICE, objects);
        OID test3 = newFolder(test2, "test3", USERID, DEVICE, objects);
        OID test4 = newFolder(test3, "test4", USERID, DEVICE, objects);
        OID test5 = newFolder(test4, "test5", USERID, DEVICE, objects);
        List<String> expectedNames =
                Lists.newArrayList("AeroFS", "test1", "test2", "test3", "test4");

        RestObject object = new RestObject(rootStore, test5);

        Object result = metadataBuilder.path(principal, object).getEntity();
        assertTrue(result instanceof ParentPath);
        List<String> resultNames = ((ParentPath) result).folders.stream()
                .map(folder -> folder.name).collect(Collectors.toList());

        assertEquals(expectedNames.size(), resultNames.size());
        assertTrue(expectedNames.containsAll(resultNames));
    }

    @Test
    public void testChildren() throws Exception
    {
        List<String> expectedNames = Lists.newArrayList();
        for (int i = 1; i <= 5; i++) {
            String name = "test" + i;
            newFolder(rootStore, name, USERID, DEVICE, objects);
            expectedNames.add(name);
        }
        RestObject object = RestObject.fromString("root");

        Object result = metadataBuilder.children(principal, object, false).getEntity();
        assertTrue(result instanceof ChildrenList);
        List<String> resultNames = ((ChildrenList) result).folders.stream()
                .map(folder -> folder.name).collect(Collectors.toList());

        assertEquals(expectedNames.size(), resultNames.size());
        assertTrue(expectedNames.containsAll(resultNames));
    }

    @Test
    public void testShouldCreateFolderUnderAeroFSRoot() throws Exception
    {
        Version version = new Version(1, 3);

        Response response = metadataBuilder.create(principal, "root", "test1", version, false);
        verifyFolderCreation(response, rootStore, "test1");
    }

    @Test
    public void testShouldCreateFolderUnderFolder() throws Exception
    {
        Version version = new Version(1, 3);
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);

        Response response = metadataBuilder.create(principal,
                new RestObject(rootStore, test1).toStringFormal(), "test11", version, false);
        verifyFolderCreation(response, test1, "test11");
    }

    @Test
    public void testShouldDeleteFoldersUnderAeroFSRoot() throws Exception
    {
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        newFolder(test1, "test11", USERID, DEVICE, objects);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue(objects.children(dao, rootStore).stream().map(folder -> new String(folder.name))
                    .collect(Collectors.toList()).contains("test1"));
            assertTrue(objects.children(dao, test1).stream().map(folder -> new String(folder.name))
                    .collect(Collectors.toList()).contains("test11"));
            return null;
        });


        metadataBuilder.delete(principal, new RestObject(rootStore, test1));

        verifyChildDeleted(rootStore, "test1");
        // If test1 is deleted, so must test11
        verifyChildDeleted(test1, "test11");
    }

    @Test
    public void testShouldDeleteFoldersUnderFolder() throws Exception
    {
        OID test2 = newFolder(rootStore, "test2", USERID, DEVICE, objects);
        OID test22 = newFolder(test2, "test22", USERID, DEVICE, objects);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue(objects.children(dao, test2).stream().map(folder -> new String(folder.name))
                    .collect(Collectors.toList()).contains("test22"));
            return null;
        });

        // Verify a folder under a folder can be deleted.
        metadataBuilder.delete(principal, new RestObject(rootStore, test22));
        verifyChildDeleted(test2, "test22");
    }

    @Test(expected=CallbackFailedException.class)
    public void testShouldFailDeletingAeroFSRoot() throws Exception
    {
        metadataBuilder.delete(principal, RestObject.fromString("root"));
    }

    @Test
    public void testShouldMoveFolderFromOneFolderToAnother() throws ExInvalidID, SQLException
    {
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID test11 = newFolder(test1, "test11", USERID, DEVICE, objects);
        OID test111 = newFolder(test11, "test111", USERID, DEVICE, objects);
        OID test2 = newFolder(rootStore, "test2", USERID, DEVICE, objects);

        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertTrue(objects.children(dao, test1).stream().map(folder -> new String(folder.name))
                    .collect(Collectors.toList()).contains("test11"));
            return null;
        });


        metadataBuilder.move(principal, new RestObject(rootStore, test11),
                new RestObject(rootStore, test2).toStringFormal(), "test11");

        // Make sure test11 moved from under test1.
        dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            assertEquals(0, objects.children(dao, test1).size());
            assertTrue(objects.children(dao, test2).stream().map(folder -> new String(folder.name))
                    .collect(Collectors.toList()).contains("test11"));
            return null;
        });

        // Make sure child of test11 also moved under test2
        ((ParentPath)metadataBuilder.path(principal, new RestObject(rootStore, test111)).getEntity())
                .folders.stream().map(folder -> folder.name).collect(Collectors.toList()).contains("test2");
    }

    @Test(expected=CallbackFailedException.class)
    public void testShouldFailMovingAeroFSRoot() throws ExInvalidID, SQLException
    {
        OID test1 = newFolder(rootStore, "test1",  USERID, DEVICE, objects);

        metadataBuilder.move(principal, RestObject.fromString("root"),
                new RestObject(rootStore, test1).toStringFormal(), "AeroFS");
    }

    @Test(expected=CallbackFailedException.class)
    public void testShouldFailMovingTrash() throws ExInvalidID, SQLException
    {
        OID test1 = newFolder(rootStore, "test1",  USERID, DEVICE, objects);
        metadataBuilder.move(principal, new RestObject(rootStore, OID.TRASH),
                new RestObject(rootStore, test1).toStringFormal(), ".trash");
    }

    @Test(expected=CallbackFailedException.class)
    public void testShouldFailMovingUnderFile() throws ExInvalidID, SQLException
    {
        OID test1 = newFolder(rootStore, "test1", USERID, DEVICE, objects);
        OID file1 = newFile(rootStore, "testFile1", USERID, DEVICE, objects);
        metadataBuilder.move(principal, new RestObject(rootStore, test1),
                new RestObject(rootStore, file1).toStringFormal(), "test1");
    }
}
