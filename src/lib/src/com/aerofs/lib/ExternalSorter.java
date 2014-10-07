package com.aerofs.lib;

import com.aerofs.base.C;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Sort a data set that may not completely fit in memory at one time.
 *
 * This class keeps track of the max size of the in-memory elements, and when
 * this is exceeded it sorts them and writes them out to disk. After all the
 * elements have been added, it then merges the on-disk files to provide the
 * results in sorted order.
 */
public class ExternalSorter<T> implements Closeable
{
    /**
     * Wrap an IOException in an unchecked exception
     */
    static class RuntimeIOException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public RuntimeIOException(IOException cause)
        {
            super(Preconditions.checkNotNull(cause));
        }
    }

    /**
     * Interface for writing elements to disk
     */
    public interface Output<T> extends Closeable
    {
        public void write(@Nonnull T value) throws IOException;
    }

    /**
     * Iterator-like interface for reading elements from disk
     */
    public interface Input<T> extends Closeable
    {
        public boolean hasNext() throws IOException;

        public T next() throws IOException;
    }

    /**
     * Like PeekingIterator but with IOExceptions
     */
    public interface PeekingInput<T> extends Input<T>
    {
        public T peek() throws IOException;
    }

    public static abstract class AbstractInput<T> implements Input<T>
    {
        private enum State
        {
            NOT_READY,
            READY,
            DONE,
            FAILED,
        }

        private State state = State.NOT_READY;
        private T next;

        protected abstract T computeNext() throws IOException;

        protected final T endOfData()
        {
            state = State.DONE;
            return null;
        }

        @Override
        public final boolean hasNext() throws IOException
        {
            checkState(state != State.FAILED);
            switch (state) {
            case DONE:
                return false;
            case READY:
                return true;
            default:
            }
            return tryToComputeNext();
        }

        private boolean tryToComputeNext() throws IOException
        {
            state = State.FAILED;
            next = computeNext();
            if (state != State.DONE) {
                state = State.READY;
                return true;
            }
            return false;
        }

        @Override
        public final T next() throws IOException
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            state = State.NOT_READY;
            T current = next;
            next = null;
            return current;
        }

        public final T peek() throws IOException
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return next;
        }
    }

    private static class PeekingImpl<T> implements PeekingInput<T>
    {
        private final Input<? extends T> _input;
        private boolean _hasPeeked;
        private T _peekedElement;

        public PeekingImpl(Input<? extends T> input)
        {
            _input = checkNotNull(input);
        }

        @Override
        public boolean hasNext() throws IOException
        {
            return _hasPeeked || _input.hasNext();
        }

        @Override
        public T next() throws IOException
        {
            if (!_hasPeeked) return _input.next();
            T result = _peekedElement;
            _hasPeeked = false;
            _peekedElement = null;
            return result;
        }

        @Override
        public T peek() throws IOException
        {
            if (!_hasPeeked) {
                _peekedElement = _input.next();
                _hasPeeked = true;
            }
            return _peekedElement;
        }

        @Override
        public void close() throws IOException
        {
            _input.close();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> PeekingInput<T> peekingInput(Input<? extends T> input)
    {
        if (input instanceof PeekingInput<?>) {
            PeekingInput<T> peeking = (PeekingInput<T>)input;
            return peeking;
        }
        return new PeekingImpl<T>(input);
    }

    private static final class StreamInput<T> extends AbstractInput<T> implements PeekingInput<T>
    {
        private final ObjectInputStream _in;

        private StreamInput(ObjectInputStream in)
        {
            _in = in;
        }

        @Override
        protected T computeNext() throws IOException
        {
            Object object;
            try {
                object = _in.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
            if (object == null) {
                close();
                return endOfData();
            }
            @SuppressWarnings("unchecked")
            T next = (T)object;
            return next;
        }

        @Override
        public void close() throws IOException
        {
            _in.close();
        }
    }

    private static final class IteratorInput<T> extends AbstractInput<T> implements PeekingInput<T>
    {
        private final Iterator<? extends T> _iterator;

        public IteratorInput(Iterator<? extends T> iterator)
        {
            _iterator = iterator;
        }

        @Override
        protected T computeNext() throws IOException
        {
            if (!_iterator.hasNext()) return endOfData();
            return _iterator.next();
        }

        @Override
        public void close() throws IOException
        {
        }
    }

    public static <T> Input<T> iteratorInput(Iterator<? extends T> iterator)
    {
        return new IteratorInput<T>(iterator);
    }

    private static void closeAll(Iterable<? extends Closeable> closeables) throws IOException
    {
        IOException ex = null;
        for (Closeable c : closeables) {
            try {
                c.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        if (ex != null) throw ex;
    }

    /**
     * The merger uses a {@link PriorityQueue} to perform a merge sort on the
     * files
     */
    static class Merger<T> extends AbstractInput<T> implements PeekingInput<T>
    {
        private PriorityQueue<PeekingInput<T>> _pq;

        public Merger(Collection<? extends Input<? extends T>> sources,
                      final Comparator<? super T> comparator) throws IOException
        {
            final Comparator<PeekingInput<T>> inputComparator = new Comparator<PeekingInput<T>>() {
                @Override
                public int compare(PeekingInput<T> o1, PeekingInput<T> o2)
                {
                    try {
                        if (!o1.hasNext()) {
                            if (!o2.hasNext()) return 0;
                            else return -1;
                        } else {
                            if (!o2.hasNext()) return 1;
                            else return comparator.compare(o1.peek(), o2.peek());
                        }
                    } catch (IOException e) {
                        throw new RuntimeIOException(e);
                    }
                }
            };
            _pq = new PriorityQueue<PeekingInput<T>>(Math.max(1, sources.size()), inputComparator);
            try {
                for (Input<? extends T> it : sources) {
                    PeekingInput<T> pi = peekingInput(it);
                    if (pi.hasNext()) _pq.add(pi);
                }
            } catch (RuntimeIOException e) {
                throw (IOException)e.getCause();
            } catch (IOException e) {
                throw e;
            }
        }

        @Override
        protected T computeNext() throws IOException
        {
            try {
                PeekingInput<T> pi = _pq.poll();
                if (pi == null) return endOfData();
                T value = pi.next();
                if (pi.hasNext()) _pq.add(pi);
                return value;
            } catch (RuntimeIOException e) {
                throw (IOException)e.getCause();
            }
        }

        @Override
        public void close() throws IOException
        {
            try {
                closeAll(_pq);
            } finally {
                _pq.clear();
            }
        }
    }

    private static final Comparator<Object> DEFAULT_COMPARATOR = new Comparator<Object>() {
        @SuppressWarnings("unchecked")
        @Override
        public int compare(Object o1, Object o2)
        {
            return ((Comparable<Object>)o1).compareTo(o2);
        }
    };

    static File _topSorterTempDir;

    final Comparator<? super T> _comparator;

    long _maxCurrentSize = 64 * C.KB;

    File _tempDir;

    Collection<File> _files = Lists.newArrayList();

    Collection<Closeable> _closeables = Lists.newArrayList();

    List<T> _current = Lists.newArrayList();

    long _currentSize;

    /**
     * Only works if T implements Comparable<? super T>
     */
    public ExternalSorter()
    {
        this(DEFAULT_COMPARATOR);
    }

    public ExternalSorter(Comparator<? super T> comparator)
    {
        _comparator = comparator;
    }

    /**
     * Set the max size of elements to keep in memory at one time
     */
    public void setMaxSize(long value)
    {
        _maxCurrentSize = value;
    }

    /**
     * Override to return a different measure of the size of one element
     */
    protected long getSize(T value)
    {
        return 1;
    }

    private static synchronized File getTopSorterTempDir() throws IOException
    {
        if (_topSorterTempDir == null) {
            _topSorterTempDir = FileUtil.createTempDir("sorter-", ".tmp", null, true);
        }
        return _topSorterTempDir;
    }

    protected File newTempFile() throws IOException
    {
        if (_tempDir == null) {
            _tempDir = FileUtil.createTempDir("sortdir-", ".tmp", getTopSorterTempDir(), true);
        }
        File f = new File(_tempDir, String.format("sortfile-%06d.tmp", _files.size()));
        FileUtil.deleteOnExit(f);
        _files.add(f);
        return f;
    }

    /**
     * Open a file for writing to disk.
     *
     * By default this uses Java's built-in serialization mechanism but with
     * periodic calls to {@link ObjectOutputStream#reset()} to limit the number
     * of objects the {@link ObjectInputStream} has to remember.
     */
    protected Output<T> openOutput(File file) throws IOException
    {
        @SuppressWarnings("resource") //we need to return the opened resource
        final ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(
                new FileOutputStream(file)));
        Output<T> out = new Output<T>() {
            ObjectOutputStream _out = os;
            int _count = 0;
            static final int MAX_COUNT = 1024;

            @Override
            public void write(@Nonnull T value) throws IOException
            {
                Preconditions.checkNotNull(value);
                _out.writeObject(value);
                if (++_count >= MAX_COUNT) {
                    _count = 0;
                    _out.reset();
                }
            }

            @Override
            public void close() throws IOException
            {
                _out.writeObject(null);
                _out.close();
            }
        };
        return out;
    }

    /**
     * Open a file for reading from disk.
     */
    protected Input<T> openInput(File file) throws IOException
    {
        final ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(
                new FileInputStream(file)));
        return new StreamInput<T>(in);
    }

    /**
     * Add an element of the given size to the sorter
     */
    public void add(T value, long size) throws IOException
    {
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(size > 0, "size > 0");
        if (_currentSize + size > _maxCurrentSize) flush();
        _current.add(value);
        _currentSize += size;
        if (_currentSize > _maxCurrentSize) flush();
    }

    /**
     * Add an element of the default size to the sorter
     */
    public void add(T value) throws IOException
    {
        add(value, getSize(value));
    }

    /**
     * Flush the in-memory elements to disk
     */
    public void flush() throws IOException
    {
        if (_current.isEmpty()) return;

        Collections.sort(_current, _comparator);

        File tempFile = newTempFile();
        Output<T> out = openOutput(tempFile);
        try {
            for (T value : _current) {
                out.write(value);
            }
        } finally {
            out.close();
        }

        _current.clear();
        _currentSize = 0;
    }

    /**
     * Iterate over all the elements in sorted order
     */
    public Input<T> sort() throws IOException
    {
        List<Input<T>> inputs = Lists.newArrayList();
        for (File f : _files) {
            inputs.add(openInput(f));
        }
        if (!_current.isEmpty()) {
            Collections.sort(_current, _comparator);
            inputs.add(iteratorInput(_current.iterator()));
        }
        return new Merger<T>(inputs, _comparator);
    }

    /**
     * Close and delete all files used by the sorter
     */
    @Override
    public void close() throws IOException
    {
        if (_current == null || _files == null) return;
        _current = null;
        for (File f : _files) {
            FileUtil.tryDeleteNow(f);
        }
        _files = null;
        if (_tempDir != null) FileUtil.tryDeleteNow(_tempDir);
    }
}
