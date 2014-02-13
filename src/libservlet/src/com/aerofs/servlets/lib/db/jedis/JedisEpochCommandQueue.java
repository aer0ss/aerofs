/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib.db.jedis;

import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.CommandType;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import redis.clients.jedis.Response;
import redis.clients.jedis.Tuple;

import javax.inject.Inject;
import java.util.Set;
import java.util.List;

/**
 * This class represents an epoch queue data structure tailored specifically for AeroFS per device
 * commands.
 *
 * The schema is as follows:
 *
 * {@value JedisEpochCommandQueue#PREFIX_QUEUE}(ueue)/DID => SortedSet<CommandType, Epoch>
 *     Desc: Set of command types sorted by epoch number. This data structure serves as the per
 *     device queue.
 *
 * {@value JedisEpochCommandQueue#PREFIX_EPOCH}(poch)/DID => Epoch
 *     Desc: the maximum epoch number for a given device.
 *
 * {@value JedisEpochCommandQueue#PREFIX_ATTEMPTS}(ttempts)/DID => Hash<CommandType, RetryAttempts>
 *     Desc: Hash of command type to an integer number of retry attempts. The hash is cleared when
 *     the command has been dequeued.
 *
 * {@value JedisEpochCommandQueue#PREFIX_CREATE}(reate)/DID => Hash<CommandType, CreationTime>
 *     Desc: Hash of command type to the creation time of the command in milliseconds. The hash is
 *     cleared when the command has been dequeued. For now this field is not used, but has been
 *     added so that we can clean the database in the future if we need to.
 *
 * Notes:
 *
 * 1. Storage of metadata is broken up so that we can take advantage of the redis incr builtin for
 *    counting retries.
 *
 * A picture helps visualize the setup in redis:
 *
 *  -----    -----    -----
 *  |A,4| => |B,3| => |C,2|
 *  -----    -----    -----
 *   |
 *   -> retry attempts
 *   -> creation timestamp
 *
 * TODO (MP) use SCRIPT LOAD / EVALSHA when Jedis supports it.
 */
public class JedisEpochCommandQueue extends AbstractJedisDatabase
{
    // Prefixes for keys in redis.
    private static final String PREFIX_QUEUE    = "jeq:q/";
    private static final String PREFIX_EPOCH    = "jeq:e/";
    private static final String PREFIX_ATTEMPTS = "jeq:a/";
    private static final String PREFIX_CREATE   = "jeq:c/";

    @Inject
    public JedisEpochCommandQueue(JedisThreadLocalTransaction transaction)
    {
        super(transaction);
    }

    //
    // Key Generators
    //

    private String getQueueKey(DID did)
    {
        return PREFIX_QUEUE + did.toStringFormal();
    }

    private String getEpochKey(DID did)
    {
        return PREFIX_EPOCH + did.toStringFormal();
    }

    private String getAttemptsKey(DID did)
    {
        return PREFIX_ATTEMPTS + did.toStringFormal();
    }

    private String getCreateKey(DID did)
    {
        return PREFIX_CREATE + did.toStringFormal();
    }

    //
    // Public Methods
    //

    /**
     * Enqueue a command for a specific device.
     *
     * Duplicate enqueues result in a bumping of the queue epoch (i.e. the command will be moved to
     * the back of the queue) and the number of attempts and creation time will remain the same.
     * This essentially means duplicate enqueues are no-ops, because delivery order does not matter.
     */
    public Epoch enqueue(DID did, CommandType commandType)
    {
        String commandTypeString = String.valueOf(commandType.getNumber());
        String currentTime = String.valueOf(System.currentTimeMillis());

        // Set the number of attempts to 0 if it hasn't already been set.
        getTransaction().eval(
                "if redis.call('hexists', KEYS[1], KEYS[2]) ~= 1 then\n" +
                "  redis.call('hset', KEYS[1], KEYS[2], 0)\n" +
                "end\n",
                2,                                       // KEYS count
                getAttemptsKey(did), commandTypeString); // KEYS

        // Set the creation time if it doesn't already exist.
        getTransaction().eval(
                "if redis.call('hexists', KEYS[1], ARGV[1]) ~= 1 then\n" +
                "  redis.call('hset', KEYS[1], ARGV[1], ARGV[2])\n" +
                "end\n",
                1,                               // KEYS count
                getCreateKey(did),               // KEYS
                commandTypeString, currentTime); // ARGV

        // Increment the epoch and add the payload to the sorted set (i.e. the device queue).
        Response<Object> response = getTransaction().eval(
                "local epoch = redis.call('incr', KEYS[1])\n" +
                "redis.call('zadd', KEYS[2], epoch, ARGV[1])\n" +
                "return epoch\n",
                2,                                  // KEYS count
                getEpochKey(did), getQueueKey(did), // KEYS
                commandTypeString);                 // ARGV

        return new Epoch(response);
    }

    /**
     * Get the size of the queue for a specific device.
     */
    public QueueSize size(DID did)
    {
        Response<Long> response = getTransaction().zcount(getQueueKey(did), 0, Long.MAX_VALUE);
        return new QueueSize(response);
    }

    /**
     * Get the head element of the queue for a specific device if it exists.
     */
    public QueueElement head(DID did)
    {
        // Get the element with the lowest score.
        Response<Set<Tuple>> response = getTransaction().zrangeByScoreWithScores(getQueueKey(did),
                0,              // min score
                Long.MAX_VALUE, // max score
                0,              // offset
                1);             // count

        return new QueueElement(response);
    }

    /**
     * Remove the entry at the head of this device's queue if the epoch number matches.
     * @return success if the element was successfully dequeued, error otherwise.
     */
    public SuccessError dequeue(DID did, long epoch)
    {
        // Use count and command locals to avoid using lua lists which are expensive.
        Response<Object> response = getTransaction().eval(
                "local count = redis.call('zcount', KEYS[1], 0, KEYS[4])\n" +
                "local command = redis.call('zrangebyscore', KEYS[1], KEYS[4], KEYS[4])[1]\n" +
                "local result = false\n" +
                "if command and count == 1 then\n" +
                "  redis.call('zrem', KEYS[1], command)\n" +
                "  redis.call('hdel', KEYS[2], command)\n" +
                "  redis.call('hdel', KEYS[3], command)\n" +
                "  result = true\n" +
                "end\n" +
                "return result\n",
                4,
                getQueueKey(did), getAttemptsKey(did), getCreateKey(did), String.valueOf(epoch));

        return new SuccessError(response);
    }

    /**
     * Move a the head element of the device's queue to the back of the queue and bump the number
     * of attempts counter for the command so that it can be retried later.
     * @return success if the element was successfully found and reseeded, error otherwise.
     */
    public SuccessError retryLater(DID did, long epoch)
    {
        Response<Object> response = getTransaction().eval(
                "local count = redis.call('zcount', KEYS[1], 0, KEYS[4])\n" +
                "local command = redis.call('zrangebyscore', KEYS[1], KEYS[4], KEYS[4])[1]\n" +
                "local result = false\n" +
                "if command and count == 1 then\n" +
                "  redis.call('hincrby', KEYS[2], command, 1)\n" +
                "  local epoch = redis.call('incr', KEYS[3])\n" +
                "  redis.call('zadd', KEYS[1], epoch, command)\n" +
                "  result = true\n" +
                "end\n" +
                "return result\n",
                4,
                getQueueKey(did), getAttemptsKey(did), getEpochKey(did), String.valueOf(epoch));

        return new SuccessError(response);
    }

    /**
     * Flush the database of all traces of this device.
     */
    public void delete(DID did)
    {
        getTransaction().del(getQueueKey(did));
        getTransaction().del(getEpochKey(did));
        getTransaction().del(getAttemptsKey(did));
        getTransaction().del(getCreateKey(did));
    }

    /**
     * Delete commands in the queue where:
     *
     *  1. retries > maxAttempts,
     *  2. creationDate < earliestDate, and
     *  3. the command type matches the type parameter.
     *
     * N.B. this command is slooow because it is only meant to be run periodicially to clean up old
     * entries.
     *
     * @return the number of queue entries deleted.
     */
    public DeletedElementCount delete(int maxAttempts, long earliestDate,
            CommandType deleteCommandType)
    {
        Response<Object> response = getTransaction().eval(
                "local total_deleted = 0\n" +
                "local max_attempts = tonumber(KEYS[1])\n" +
                "local earliest_date = tonumber(KEYS[2])\n" +
                "local delete_command = tonumber(KEYS[3])\n" +
                "local prefix_attempts = KEYS[4]\n" +
                "local prefix_create = KEYS[5]\n" +
                "local prefix_queue = KEYS[6]\n" +
                "local keys = redis.call('keys', prefix_attempts .. '*')\n" +
                "for i = 1, #keys\n" +
                "  do\n" +
                "  local attempt_key = keys[i]\n" +
                "  local command_keys = redis.call('hkeys', attempt_key)\n" +
                "  for j = 1, #command_keys\n" +
                "  do\n" +
                "    local did = attempt_key:sub((attempt_key:find('/')+1))\n" +
                "    local command = command_keys[j]\n" +
                "    local attempts = tonumber(redis.call('hget', attempt_key, command))\n" +
                "    local creation_time =" +
                "        tonumber(redis.call('hget', prefix_create .. did, command))\n" +
                "    if attempts > max_attempts and" +
                "        tonumber(command) == delete_command and" +
                "        creation_time < earliest_date then\n" +
                "      redis.call('hdel', prefix_create .. did, command)\n" +
                "      redis.call('hdel', attempt_key, command)\n" +
                "      redis.call('zrem', prefix_queue .. did, command)\n" +
                "      total_deleted = total_deleted + 1\n" +
                "    end\n" +
                "  end\n" +
                "end\n" +
                "return total_deleted\n",
                6, // KEYS count
                String.valueOf(maxAttempts),
                String.valueOf(earliestDate),
                String.valueOf(deleteCommandType.getNumber()),
                PREFIX_ATTEMPTS, PREFIX_CREATE, PREFIX_QUEUE); // KEYS

        return new DeletedElementCount(response, deleteCommandType);
    }

    public DeletedElementCountList delete(int maxAttempts, long earliestDate)
    {
        List<DeletedElementCount> result = Lists.newLinkedList();

        int counter = 0;
        CommandType type;

        // Loop through all types of commands.
        while ((type = CommandType.valueOf(counter++)) != null) {
            result.add(delete(maxAttempts, earliestDate, type));
        }

        return new DeletedElementCountList(result);
    }

    //
    // Static Classes
    //
    // These classes are used to wrap return results for the above functions. In this way the caller
    // does not have to know how to cast redis return values. For example, we don't want the client
    // to have to guess how to cast Object to Long when calling get() on the Response<> object.
    //
    // These classes are tighly coupled to the implementation of the above lua scripts, so keep them
    // in this file.
    //

    public static class QueueSize
    {
        private final Response<Long> _response;

        public QueueSize(Response<Long> response)
        {
            _response = response;
        }

        public long getSize()
        {
            return _response.get();
        }
    }

    public static class DeletedElementCount
    {
        private final Response<Object> _response;
        private final CommandType _type;

        public DeletedElementCount(Response<Object> response, CommandType type)
        {
            _response = response;
            _type = type;
        }

        public long getCount()
        {
            return (Long) _response.get();
        }

        public CommandType getType()
        {
            return _type;
        }
    }

    public static class DeletedElementCountList
    {
        private final List<DeletedElementCount> _countList;

        public DeletedElementCountList(List<DeletedElementCount> countList)
        {
            _countList = countList;
        }

        public List<DeletedElementCount> getCountList()
        {
            return _countList;
        }

        public long getCount()
        {
            long count = 0;

            for (DeletedElementCount element : getCountList()) {
                count += element.getCount();
            }

            return count;
        }
    }

    public static class QueueElement
    {
        private final Response<Set<Tuple>> _response;
        private boolean _init = false;

        private boolean _exists = false;
        private long _epoch = 0;
        private CommandType _type = null;

        public QueueElement(Response<Set<Tuple>> response)
        {
            _response = response;
        }

        private synchronized void init()
        {
            if (!_init) {
                Set<Tuple> tupleSet = _response.get();
                int size = tupleSet.size();

                if (size == 0) {
                    return;
                }

                // Guaranteed size 1 by our choice of params to zrange in the head function, above.
                final Tuple t = Iterables.getOnlyElement(tupleSet);
                _epoch = (long) t.getScore();
                _type = CommandType.valueOf(Integer.valueOf(t.getElement()));

                // The Iterables call will fail so we know this is true at this point.
                _exists = true;
            }

            _init = true;
        }

        public boolean exists()
        {
            init();
            return _exists;
        }

        public long getEpoch()
        {
            init();
            return _epoch;
        }

        public CommandType getType()
        {
            init();
            return _type;
        }
    }

    public static class SuccessError
    {
        private final Response<Object> _response;

        public SuccessError(Response<Object> response)
        {
            _response = response;
        }

        public boolean success()
        {
            Long response = (Long) _response.get();
            return response != null && response == 1;
        }

        public boolean error()
        {
            return !success();
        }
    }

    public static class Epoch
    {
        private final Response<Object> _response;

        public Epoch(Response<Object> response)
        {
            _response = response;
        }

        public long get()
        {
            return (Long) _response.get();
        }
    }
}