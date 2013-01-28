/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.SuccessError;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.DeletedElementCount;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.DeletedElementCountList;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueSize;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.QueueElement;
import org.junit.Before;
import org.junit.Test;
import junit.framework.Assert;
import org.junit.runner.RunWith;

import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

// We will be mocking statics, so use power mock.
@RunWith(PowerMockRunner.class)
// The statics exist in the JedisEpochCommandQueue class.
@PrepareForTest(JedisEpochCommandQueue.class)
public class TestJedisEpochCommandQueue extends AbstractJedisTest
{
    private JedisEpochCommandQueue _queue = new JedisEpochCommandQueue(getTransaction());

    private final DID _d1 = new DID(UniqueID.generate());

    private final CommandType _c1 = CommandType.INVALIDATE_DEVICE_NAME_CACHE;
    private final CommandType _c2 = CommandType.INVALIDATE_USER_NAME_CACHE;

    @Before
    public void setupTestJedisEpochCommandQueue() throws ExFormatError
    {
        // Mock the system time static method.
        PowerMockito.mockStatic(System.class);
        Mockito.when(System.currentTimeMillis()).thenReturn(0L);
    }

    //
    // Utils
    //

    /**
     * Enqueue command c1 for device d1.
     */
    private void enqueueD1(CommandType commandType)
    {
        getTransaction().begin();
        _queue.enqueue(_d1, commandType);
        getTransaction().commit();
    }

    /**
     * Retry the command at the head of the d1 queue later.
     */
    private void retryLater(DID did)
    {
        getTransaction().begin();
        QueueElement head = _queue.head(did);
        getTransaction().commit();

        Assert.assertTrue(head.exists());

        getTransaction().begin();
        SuccessError result = _queue.retryLater(did, head.getEpoch());
        getTransaction().commit();

        Assert.assertTrue(result.success());
    }

    /**
     * Expect the queue for d1 to be of size 1 and to contain the enqueued command c1.
     */
    private void expectD1C1()
    {
        getTransaction().begin();
        QueueElement element = _queue.head(_d1);
        QueueSize size = _queue.size(_d1);
        getTransaction().commit();

        Assert.assertEquals(1, size.getSize());
        Assert.assertEquals(_c1, element.getType());
    }

    /**
     * Expect the queue for device 1 to be empty.
     */
    private void expectD1Empty()
    {
        getTransaction().begin();
        QueueElement element = _queue.head(_d1);
        QueueSize size = _queue.size(_d1);
        getTransaction().commit();

        Assert.assertFalse(element.exists());
        Assert.assertEquals(0, size.getSize());
    }

    private QueueElement getHead(DID did)
    {
        getTransaction().begin();
        QueueElement head = _queue.head(did);
        getTransaction().commit();

        return head;
    }

    //
    // Tests
    //

    @Test
    public void testSingleEnqueue()
    {
        enqueueD1(_c1);
        expectD1C1();
    }

    @Test
    public void testSingleDequeue()
    {
        enqueueD1(_c1);
        expectD1C1();

        QueueElement head = getHead(_d1);

        // Dequeue the head element.
        getTransaction().begin();
        SuccessError result = _queue.dequeue(_d1, head.getEpoch());
        getTransaction().commit();

        expectD1Empty();
        Assert.assertTrue(result.success());
    }

    @Test
    public void testSingleEnqueueDeduplication()
    {
        enqueueD1(_c1);
        enqueueD1(_c1);
        expectD1C1();
    }

    @Test
    public void testMultipleEnqueue()
    {
        enqueueD1(_c1);
        enqueueD1(_c2);
        enqueueD1(_c1);

        getTransaction().begin();
        QueueElement head = _queue.head(_d1);
        QueueSize size = _queue.size(_d1);
        getTransaction().commit();

        Assert.assertEquals(2, size.getSize());
        Assert.assertEquals(_c2, head.getType());
    }

    @Test
    public void testDeleteWithDID()
    {
        enqueueD1(_c1);

        getTransaction().begin();
        _queue.delete(_d1);
        getTransaction().commit();

        expectD1Empty();
    }

    @Test
    public void testDeleteWithMaxRetriesAndEarliestDate()
    {
        enqueueD1(_c1);
        retryLater(_d1);
        retryLater(_d1);

        getTransaction().begin();
        DeletedElementCount count = _queue.delete(1, 1L, _c1);
        getTransaction().commit();

        expectD1Empty();
        Assert.assertEquals(1L, count.getCount());
        Assert.assertEquals(_c1, count.getType());
    }

    @Test
    public void testDeleteWithMaxRetriesAndEarliestDateAndType()
    {
        enqueueD1(_c1);
        retryLater(_d1);
        retryLater(_d1);

        getTransaction().begin();
        DeletedElementCountList countList = _queue.delete(1, 1L);
        getTransaction().commit();

        expectD1Empty();
        Assert.assertEquals(1L, countList.getCount());
    }

    @Test
    public void testMultipleDeleteWithMaxRetriesAndEarliestDate()
    {
        enqueueD1(_c1);
        retryLater(_d1); // c1 attempt #1
        retryLater(_d1); // c1 attempt #2
        enqueueD1(_c2);
        retryLater(_d1); // c1 attempt #3
        retryLater(_d1); // c2 attempt #1
        retryLater(_d1); // c1 attempt #4
        retryLater(_d1); // c2 attempt #2

        getTransaction().begin();
        DeletedElementCountList countList = _queue.delete(1, 1L); // all attempted more than once.
        getTransaction().commit();

        expectD1Empty();
        Assert.assertEquals(2L, countList.getCount());
    }

    @Test
    public void testEpochCounterForSingleQueue()
    {
        QueueElement head;

        for (long i = 1; i <= 10; i++) {
            enqueueD1(_c1);
            head = getHead(_d1);
            Assert.assertEquals(i, head.getEpoch());
        }
    }
}