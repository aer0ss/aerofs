package com.aerofs.lib.bf;

public interface IBFHash<E> {

        public int length(); // Length in bits of bit-vector to which this maps

        public int [] hash(E element); // Hash function tuple that returns an array of values
}
