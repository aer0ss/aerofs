/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib.db.jedis;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.AbstractJedisTest;
import org.junit.Assert;
import org.junit.Test;

import static com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.*;
import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static org.mockito.Mockito.mock;

public class TestJedisEpochCommandQueue extends AbstractJedisTest
{
    private TimeSource timeSource = mock(TimeSource.class);

    private JedisEpochCommandQueue _queue = new JedisEpochCommandQueue(getTransaction(), timeSource);

    private final DID _d1 = new DID(UniqueID.generate());

    private final CommandType _ct1 = CommandType.INVALIDATE_DEVICE_NAME_CACHE;
    private final String _c1 = createCommandMessage(_ct1);
    private final String _c2 = createCommandMessage(CommandType.INVALIDATE_USER_NAME_CACHE);

    //
    // Utils
    //

    /**
     * Enqueue command message for device d1.
     */
    private void enqueueD1(String commandMessage)
    {
        getTransaction().begin();
        _queue.enqueue(_d1, commandMessage);
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
     * Expect the queue for d1 to be of size 1 and to contain the enqueued command message c1.
     */
    private void expectD1C1()
    {
        expectD1SizeAndHead(1, _c1);
    }

    private void expectD1SizeAndHead(int expectedSize, String expectedMessage)
    {
        getTransaction().begin();
        QueueElement element = _queue.head(_d1);
        QueueSize size = _queue.size(_d1);
        getTransaction().commit();

        Assert.assertEquals(expectedSize, size.getSize());
        Assert.assertEquals(expectedMessage, element.getCommandMessage());
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

    private DeletedElementCount deleteType(CommandType type)
    {
        getTransaction().begin();
        // max_attempt := -1 => delete commands regardless of # of attempts
        DeletedElementCount deleted = _queue.delete(-1, 1L, type);
        getTransaction().commit();

        return deleted;
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

        expectD1SizeAndHead(2, _c2);
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
        DeletedElementCount count = _queue.delete(1, 1L, _ct1);
        getTransaction().commit();

        expectD1Empty();
        Assert.assertEquals(1L, count.getCount());
        Assert.assertEquals(_ct1, count.getType());
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

    @Test
    public void shouldDeleteCommandsWithArgsOfSameType()
    {
        String c1WithArgs = _c1 + ":some_args";
        String c1WithOtherArgs = _c1 + ":some_other_args";
        enqueueD1(_c1);
        enqueueD1(_c2);
        enqueueD1(c1WithArgs);
        enqueueD1(c1WithOtherArgs);

        Assert.assertEquals(3, deleteType(_ct1).getCount());
        expectD1SizeAndHead(1, _c2);
    }

    @Test
    public void shouldNotDeleteCommandsWithArgsOfDifferentType()
    {
        String c2WithArgs = _c2 + ":some_args";
        enqueueD1(_c1);
        enqueueD1(c2WithArgs);

        Assert.assertEquals(1, deleteType(_ct1).getCount());
        expectD1SizeAndHead(1, c2WithArgs);
    }
}
