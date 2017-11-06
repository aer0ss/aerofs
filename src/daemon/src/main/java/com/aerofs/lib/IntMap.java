package com.aerofs.lib;

import com.koloboke.compile.KolobokeMap;

@KolobokeMap
public abstract class IntMap<T> {
    public static <T> IntMap<T> withExpectedSize(int size) {
        return new KolobokeIntMap<T>(size);
    }

    public abstract T put(int k, T v);
    public abstract T get(int k);
    public abstract T remove(int k);
}
