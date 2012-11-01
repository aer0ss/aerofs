/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.testlib.AbstractTest;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TestSQLThreadLocalTransaction extends AbstractTest
{
    private class TransactionTestDatabaseParams extends DatabaseParameters
    {
        private static final String TX_SCHEMA_PATH_PARAMETER = "junit.mysqlTxSchemaPath";
        private static final String DEFAULT_TX_SCHEMA_PATH = "../../src/libservlet/test/resources";
        private final String _mysqlSchemaPath;

        public TransactionTestDatabaseParams()
        {
            _mysqlSchemaPath = getOrDefault(TX_SCHEMA_PATH_PARAMETER, DEFAULT_TX_SCHEMA_PATH);
        }

        @Override
        public String getMySQLSchemaPath()
        {
            return _mysqlSchemaPath;
        }

        @Override
        public String getMySQLDatabaseName()
        {
            return "transaction_test";
        }

        @Override
        public String getMySQLSchemaName()
        {
            return "transaction_test.sql";
        }
    }

   private final DatabaseParameters _dbParams = new TransactionTestDatabaseParams();
    @Spy private final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());

    @Before
    public void setup()
            throws Exception
    {
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);
    }

    @After
    public void tearDown()
            throws Exception
    {
        _transaction.cleanUp(); // run cleanup here to check that connections are all closed
    }

    /**
     * Adds the given user id to the users table.
     */
    private void addUser(String userId)
            throws SQLException
    {
        PreparedStatement addUser =
                _transaction.getConnection().prepareStatement("insert into users values(?)");

        addUser.setString(1, userId);
        addUser.executeUpdate();
    }

    /**
     * Checks that the given user id is in the user table
     */
    private boolean isUserInDatabase(String id)
            throws SQLException
    {
        PreparedStatement getUser =
                _transaction.getConnection().prepareStatement("select * from users where u_id=?");

        getUser.setString(1, id);
        ResultSet rs = getUser.executeQuery();
        try {
            return rs.next() && !rs.next(); // one and only one result matching
        } finally {
            rs.close();
        }
    }

    private void initializeDummyData()
            throws SQLException
    {
        // add users with IDs "A" - "Z" and one data row for each of them
        for (char id = 'A'; id <= 'Z'; id++) {
            String userId = "" + id;
            addUser(userId);
        }
    }

    /**
     * Check that users table is empty
     */
    private boolean isUserTableEmpty() throws Exception
    {
        _transaction.begin();
        PreparedStatement getAllUsers =
                _transaction.getConnection().prepareStatement("select * from users");
        ResultSet rs = getAllUsers.executeQuery();
        boolean retVal = !rs.next();
        _transaction.commit();

        return retVal;
    }

    @Test
    public void shouldCommitDataToTable() throws Exception
    {
        _transaction.begin();
        initializeDummyData();
        _transaction.commit();

        // check that data was actually committed to the table
        _transaction.begin();
        for (char id = 'A'; id <= 'Z'; id++) {
            Assert.assertTrue(isUserInDatabase(id + ""));
        }
        _transaction.commit();
    }

    @Test
    public void shouldNotCommitDataAfterRollback() throws Exception
    {
        _transaction.begin();
        initializeDummyData();
        _transaction.rollback(); // should throw away all changes

        Assert.assertTrue(isUserTableEmpty());
    }

    @Test
    public void shouldNotCommitDataAfterHandleException() throws Exception
    {
        _transaction.begin();
        initializeDummyData();
        _transaction.handleException(); // should throw away all changes

        Assert.assertTrue(isUserTableEmpty());
    }

    @Test
    public void shouldNotCommitOnDuplicateKeyException() throws Exception
    {
        _transaction.begin();
        initializeDummyData();
        _transaction.commit();

        String user1 = "ABC";
        String user2 = "DEF";

        try {
            _transaction.begin();
            addUser(user1);
            addUser(user2);
            initializeDummyData(); // doing this again will cause a duplicate key error
        } catch (SQLException e) {
            _transaction.handleException(); // cleans up after the transaction
        }

        _transaction.begin();
        Assert.assertTrue(!isUserInDatabase(user1) && !isUserInDatabase(user2));
        _transaction.commit();
    }

    @Test
    public void shouldAssertOnNestedTransactions() throws Exception
    {
        try {
            _transaction.begin();
            _transaction.begin(); // should cause AE
        } catch (AssertionError e) {
            _transaction.handleException();
        }
    }

    @Test
    public void shouldAssertOnCleanupWhenTransactionIsOpen() throws Exception
    {
        try {
            _transaction.begin();
            _transaction.cleanUp(); // should cause an AE
            Assert.fail();
        } catch (AssertionError e) {}
    }

    @Test
    public void shouldAssertOnGetConnectionWhenNotInTransaction() throws Exception
    {
        try {
            _transaction.getConnection(); // should cause an AE
            Assert.fail();
        } catch (AssertionError e) {}
    }

    @Test
    public void shouldAssertOnCommitWhenNotInTransaction() throws Exception
    {
        try {
            _transaction.commit(); // should cause an AE
            Assert.fail();
        } catch (AssertionError e) {}
    }

    @Test
    public void shouldAssertOnRollbackWhenNotInTransaction() throws Exception
    {
        try {
            _transaction.rollback(); // should cause an AE
            Assert.fail();
        } catch (AssertionError e) {}
    }

    @Test
    public void shouldAllowHandleExceptionWhenNotInTransaction() throws Exception
    {
        _transaction.handleException();
    }

    @Test
    public void shouldAllowCleanUpWhenNotInTransaction() throws Exception
    {
        _transaction.cleanUp();
    }
}
