package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.LinkedRevFile;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LogUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aerofs.lib.LogUtil.Level;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLinkedRevProvider extends AbstractTest
{
    @Mock LinkedRevProvider.TimeSource ts;
    @Mock private CfgAbsRoots cfgAbsRoots;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private InjectableFile.Factory factFile;
    private InjectableFile rootDir;
    private InjectableFile dataDir;
    private InjectableFile revDir;
    private PrintWriter out;
    private LinkedRevProvider localRevProvider;

    final SID rootSID = SID.generate();

    @Before
    public void before() throws Exception
    {
        AppRoot.set("dummy");

        LogUtil.setLevel(TestLinkedRevProvider.class, Level.INFO);
        out = new PrintWriter(System.out);

        factFile = new InjectableFile.Factory();

        rootDir = factFile.create(tempFolder.getRoot().getPath());
        dataDir = factFile.create(rootDir, "data");
        dataDir.mkdirs();
        String auxDir = Cfg.absAuxRootForPath(dataDir.getAbsolutePath(), rootSID);
        revDir = factFile.create(auxDir, Param.AuxFolder.REVISION._name);
        revDir.mkdirs();

        when(cfgAbsRoots.get(rootSID)).thenReturn(rootDir.getAbsolutePath());
        when(cfgAbsRoots.get()).thenReturn(ImmutableMap.of(rootSID, rootDir.getAbsolutePath()));

        LinkedStorage s = mock(LinkedStorage.class);
        when(s.auxRootForStore_(rootSID)).thenReturn(auxDir);

        localRevProvider = new LinkedRevProvider(s, factFile);
        localRevProvider._startCleanerScheduler = false;
        localRevProvider.init_();
    }

    @After
    public void after() throws Exception
    {
        LogUtil.setLevel(TestLinkedRevProvider.class, Level.NONE);
        out.flush();
    }

    @Test
    public void shouldSaveRevFileInAuxDirectory() throws Exception
    {
        InjectableFile test1 = factFile.create(dataDir, "test1");
        writeFile(test1, "test1");
        LinkedRevFile localRevFile = localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(rootSID, dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, dataDir.list().length);
        Assert.assertEquals(0, revDir.list().length);
        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, dataDir.list().length);
        Assert.assertEquals(1, revDir.list().length);
    }

    @Test
    public void shouldRollbackToOriginalLocation() throws Exception
    {
        InjectableFile test1 = factFile.create(dataDir, "test1");
        writeFile(test1, "test1");
        LinkedRevFile localRevFile = localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(rootSID, dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, dataDir.list().length);
        Assert.assertEquals(0, revDir.list().length);
        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, dataDir.list().length);
        Assert.assertEquals(1, revDir.list().length);
        localRevFile.rollback_();
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, dataDir.list().length);
        Assert.assertEquals(0, revDir.list().length);
    }

    @Test
    public void shouldCleanOldFiles() throws Exception
    {
        final List<InjectableFile> deletedFiles = Lists.newArrayList();
        LinkedRevProvider.Cleaner cleaner = localRevProvider.new Cleaner() {
            @Override
            long getSpaceLimit(String absRoot)
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
        InjectableFile test1 = factFile.create(dataDir, "test1");
        writeFile(test1, "test1");
        test1.setLastModified(backThen.getTime());

        localRevProvider._ts = ts;
        when(ts.getTime()).thenReturn(backThen.getTime());

        test1.setLastModified(backThen.getTime());
        LinkedRevFile localRevFile = localRevProvider.newLocalRevFile_(
            Path.fromAbsoluteString(rootSID, dataDir.getPath(), test1.getPath()), test1.getPath(),
            new KIndex(0));
        Assert.assertTrue(test1.isFile());
        Assert.assertEquals(1, dataDir.list().length);
        Assert.assertEquals(0, revDir.list().length);

        localRevFile.save_();
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, dataDir.list().length);
        Assert.assertEquals(1, revDir.list().length);

        when(ts.getTime()).thenReturn(now.getTime());

        cleaner.run(revDir.getAbsolutePath());
        Assert.assertEquals(1, deletedFiles.size());
        Assert.assertTrue(!test1.isFile());
        Assert.assertEquals(0, dataDir.list().length);
        Assert.assertEquals(0, revDir.list().length);
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
            dir = factFile.create(dir, sub);
            dir.mkdirIgnoreError();
        }
        return dir;
    }

    @Test
    public void shouldCleanLotsOfFilesWithExternalSorter() throws Exception
    {
        LinkedRevProvider.Cleaner cleaner = localRevProvider.new Cleaner() {
            @Override
            long getSpaceLimit(String absPath)
            {
                return 0;
            }
        };
        cleaner._delayMillis = 0;
        long age = TimeUnit.DAYS.toMillis(15);
        Assert.assertTrue(age > cleaner._ageLimitMillis);

        Date now = new Date();
        Date backThen = new Date(now.getTime() - age);

        localRevProvider._ts = ts;
        when(ts.getTime()).thenReturn(backThen.getTime());

        int numDirs = 5;
        int numFiles = 10;

        int dirLen = String.valueOf(numDirs - 1).length();

        for (int di = 0; di < numDirs; ++di) {
            InjectableFile dir = makeNestedDirs(dataDir, di, dirLen);
            for (int fi = 0; fi < numFiles; ++fi) {
                String name = "f" + fi;
                InjectableFile file = factFile.create(dir, name);
                writeFile(file, name);
                file.setLastModified(backThen.getTime());
                LinkedRevFile localRevFile = localRevProvider.newLocalRevFile_(
                    Path.fromAbsoluteString(rootSID, dataDir.getPath(), file.getPath()), file.getPath(),
                    new KIndex(0));
                localRevFile.save_();
            }
        }

        when(ts.getTime()).thenReturn(now.getTime());

        LinkedRevProvider.Cleaner.RunData runData = cleaner.new RunData(revDir.getAbsolutePath());
        runData._sorter.setMaxSize(10);
        runData.run();
        listRecursively(rootDir);
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
        l.info(o.toString());
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
