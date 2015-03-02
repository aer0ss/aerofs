package com.aerofs.daemon.lib;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.OutArg;

/**
 * We don't use the name of "PriorityQueue" to avid conflict with
 * java.util.PriorityQueue
 */
public class PrioQueue<T> implements IDumpStatMisc
{

    // we could implement this class using PriorityQueue, but according to
    // java doc, "if multiple elements are tied for least value, the head is
    // one of those elements -- ties are broken arbitrarily."
    // TODO use sequence numbers to break ties in a PriorityQueue

    static {
        assert Prio.values().length == 2;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private final Queue<T>[] _qs = new Queue[Prio.values().length];

    private final int _cap;
    private int _size;
    private long _out;  // for statistics only

    public PrioQueue(int capacity)
    {
        _cap = capacity;

        for (int i = 0; i < Prio.values().length; i++) {
            _qs[i] = new LinkedList<T>();
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + (_size + _out) + " = " + _size + " (" + _cap +
                ") + " + _out);
    }

    @Override
    public String toString()
    {
        return (_size + _out) + " = " + _size + " (" + _cap + ") + " + _out;
    }

    public boolean isFull_()
    {
        return _size >= _cap;
    }

    /**
     * pre-condition: isFull_() == false
     */
    public void enqueue_(T e, Prio prio)
    {
        assert !isFull_();
        _qs[prio.ordinal()].add(e);
        _size++;
    }

    public int length_()
    {
        return _size;
    }

    public boolean isEmpty_()
    {
        return _size == 0;
    }

    /**
     * dequeue a message of a given priority
     *
     * pre-condition: the queue of the specified priority is not empty
     */
    public T dequeue_(Prio prio)
    {
        Queue<T> _q = _qs[prio.ordinal()];
        assert !_q.isEmpty();
        _size--;
        _out++;
        return _q.remove();
    }

    public T dequeue_()
    {
        return dequeue_((OutArg<Prio>) null);
    }

    /**
     * pre-condition: isEmpty_() == false
     */
    public T dequeue_(OutArg<Prio> outPrio)
    {
        assert !isEmpty_();
        for (int i = 0; i < _qs.length; i++) {
            if (!_qs[i].isEmpty()) {
                _size--;
                _out++;
                T e = _qs[i].remove();
                if (outPrio != null) outPrio.set(Prio.values()[i]);
                return e;
            }
        }
        assert false;
        return null;
    }

    /**
     * pre-condition: isEmpty_() == false
     */
    public T peek_(OutArg<Prio> outPrio)
    {
        assert !isEmpty_();
        for (int i = 0; i < _qs.length; i++) {
            if (!_qs[i].isEmpty()) {
                T e = _qs[i].peek();
                if (outPrio != null) outPrio.set(Prio.values()[i]);
                return e;
            }
        }
        assert false;
        return null;
    }
}
