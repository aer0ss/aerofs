package com.aerofs.ca.database;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ca.server.TestCAServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.skife.jdbi.v2.DBI;

import static org.junit.Assert.assertTrue;

public class TestCADatabase {
    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new TestCAServer());

    @Test
    public void shouldBeInitializedAfterStartup()
    {
        //TODO (RD) how do i get objects from the injector? might need HK2Runner.class from org.jvnet
    }
}
