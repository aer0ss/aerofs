package com.aerofs.testlib;

import java.io.File;
import java.io.IOException;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.aerofs.lib.FileUtil;

/**
 * Utility class that creates an empty temporary directory for the current test instance.
 *
 * The directory is created under $TMPDIR/junit-$USER/<test-class>/<test-method>. Any existing
 * directory is deleted. The directory is left around after the test runs for post-mortem
 * examination.
 */
public class UnitTestTempDir extends TestWatcher
{
    private Description _description;
    private File _testDir;

    @Override
    protected void starting(Description description)
    {
        super.starting(description);
        _description = description;
    }

    public File getTestTempDir() throws IOException
    {
        if (_testDir == null) {
            File rootDir = new File(FileUtil.getJavaTempDir(),
                    "junit-" + System.getProperty("user.name"));
            if (!rootDir.exists()) rootDir.mkdirs();
            String name = _description.getClassName() + File.separatorChar +
                    _description.getMethodName();
            File testDir = new File(rootDir, name);
            if (testDir.isDirectory()) FileUtil.deleteRecursively(testDir, null);
            FileUtil.mkdirs(testDir);
            _testDir = testDir;
        }
        return _testDir;
    }
}
