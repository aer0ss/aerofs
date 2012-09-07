package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.LinkedRevFile;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Lists;

public class TestLinkedRevProvider extends AbstractTest
{
    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private InjectableFile.Factory _factFile;
    private InjectableFile _rootDir;
    private InjectableFile _dataDir;
    private InjectableFile _auxDir;
    private InjectableFile _revDir;
    private PrintWriter _out;
    private LinkedRevProvider _localRevProvider;

    @Before
    public void before() throws Exception
    {
        LinkedRevProvider.l.setLevel(Level.INFO);
        _out = new PrintWriter(System.out);

        _factFile = new InjectableFile.Factory();

        _rootDir = _factFile.create(_tempFolder.getRoot().getPath());
        _dataDir = _factFile.create(_rootDir, "data");
        _dataDir.mkdirs();
        _auxDir = _factFile.create(_rootDir, "aux");
        _revDir = _factFile.create(_auxDir, AuxFolder.REVISION._name);

        _localRevProvider = new LinkedRevProvider(_factFile);
        _localRevProvider._startCleanerScheduler = false;
        _localRevProvider.init_(_auxDir.getPath());
    }

    @After
    public void after() throws Exception
    {
        LinkedRevProvider.l.setLevel(null);
        _out.flush();
    }

    @Test
    public void shouldSaveRevFileInAuxDirectory() throws Exception
    {
        InjectableFile test1 = _factFile.create(_dataDir, "test1");
        writeFile(test1, "test1");
        LinkedRevFile localRevFile = _localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(_dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, _dataDir.list().length);
        Assert.assertEquals(0, _revDir.list().length);
        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, _dataDir.list().length);
        Assert.assertEquals(1, _revDir.list().length);
    }

    @Test
    public void shouldRollbackToOriginalLocation() throws Exception
    {
        InjectableFile test1 = _factFile.create(_dataDir, "test1");
        writeFile(test1, "test1");
        LinkedRevFile localRevFile = _localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(_dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, _dataDir.list().length);
        Assert.assertEquals(0, _revDir.list().length);
        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, _dataDir.list().length);
        Assert.assertEquals(1, _revDir.list().length);
        localRevFile.rollback_();
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, _dataDir.list().length);
        Assert.assertEquals(0, _revDir.list().length);
    }

    @Test
    public void shouldCleanOldFiles() throws Exception
    {
        final List<InjectableFile> deletedFiles = Lists.newArrayList();
        LinkedRevProvider.Cleaner cleaner = _localRevProvider.new Cleaner() {
            @Override
            long getSpaceLimit()
            {
                return 0;
            }

            @Override
            boolean tryDeleteOldRev(InjectableFile file)
            {
                boolean rv = super.tryDeleteOldRev(file);
                if (rv) deletedFiles.add(file);
                return rv;
            }
        };

        cleaner._delayMillis = 0;
        long age = TimeUnit.DAYS.toMillis(15);
        Assert.assertTrue(age > cleaner._ageLimitMillis);

        Date now = new Date();
        Date backThen = new Date(now.getTime() - age);
        InjectableFile test1 = _factFile.create(_dataDir, "test1");
        writeFile(test1, "test1");
        test1.setLastModified(backThen.getTime());

        test1.setLastModified(backThen.getTime());
        LinkedRevFile localRevFile = _localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(_dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, _dataDir.list().length);
        Assert.assertEquals(0, _revDir.list().length);
        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, _dataDir.list().length);
        Assert.assertEquals(1, _revDir.list().length);
        cleaner.run();
        Assert.assertEquals(1, deletedFiles.size());
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, _dataDir.list().length);
        Assert.assertEquals(0, _revDir.list().length);
    }

    private InjectableFile makeNestedDirs(InjectableFile base, int i, int len)
    {
        char[] chars = new char[len + 1];
        chars[0] = 'd';
        Arrays.fill(chars, 1, chars.length, '0');
        String name = String.valueOf(i);
        name.getChars(0, name.length(), chars, chars.length - name.length());
        InjectableFile dir = base;
        for (int n = 1; n < chars.length; n += 2) {
            String sub = new String(chars, 0, Math.min(n + 2, chars.length));
            dir = _factFile.create(dir, sub);
            dir.mkdirIgnoreError();
        }
        return dir;
    }

    @Test
    public void shouldCleanLotsOfFilesWithExternalSorter() throws Exception
    {
        LinkedRevProvider.Cleaner cleaner = _localRevProvider.new Cleaner() {
            @Override
            long getSpaceLimit()
            {
                return 0;
            }
        };
        cleaner._delayMillis = 0;
        long age = TimeUnit.DAYS.toMillis(15);
        Assert.assertTrue(age > cleaner._ageLimitMillis);

        Date now = new Date();
        Date backThen = new Date(now.getTime() - age);

        int numDirs = 5;
        int numFiles = 10;

        int dirLen = String.valueOf(numDirs - 1).length();

        for (int di = 0; di < numDirs; ++di) {
            InjectableFile dir = makeNestedDirs(_dataDir, di, dirLen);
            for (int fi = 0; fi < numFiles; ++fi) {
                String name = "f" + fi;
                InjectableFile file = _factFile.create(dir, name);
                writeFile(file, name);
                file.setLastModified(backThen.getTime());
                LinkedRevFile localRevFile = _localRevProvider.newLocalRevFile_(
                    Path.fromAbsoluteString(_dataDir.getPath(), file.getPath()), file.getPath(),
                    new KIndex(0));
                localRevFile.save_();
            }
        }

        LinkedRevProvider.Cleaner.RunData runData = cleaner.new RunData();
        runData._sorter.setMaxSize(10);
        runData.run();
        listRecursively(_rootDir);
    }

    private void writeFile(InjectableFile file, String contents) throws IOException
    {
        PrintWriter pw = new PrintWriter(file.getImplementation());
        try {
            pw.print("test1");
            if (pw.checkError()) throw new IOException("error writing " + file);
        } finally {
            pw.close();
        }
    }

    private void println(Object o)
    {
        Log.info(o);
    }

    void listRecursively(InjectableFile f)
    {
        String path = f.getPath();
        // if (path.length() > _root.getPath().length() + 1 &&
        // path.startsWith(_root.getPath()) &&
        // path.charAt(_root.getPath().length()) == '/') {
        // path = path.substring(_root.getPath().length() + 1);
        // }
        if (f.isFile()) {
            println(path);
        } else if (f.isDirectory()) {
            println(path + "/");
            for (InjectableFile child : f.listFiles()) {
                listRecursively(child);
            }
        } else {
            println(path + "?");
        }
    }
}
