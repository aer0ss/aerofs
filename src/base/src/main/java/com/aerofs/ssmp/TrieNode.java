package com.aerofs.ssmp;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

class TrieNode<T> {
    private @Nullable T value;
    private @Nullable NavigableMap<String, TrieNode<T>> children;

    public TrieNode() {}

    public TrieNode(@Nonnull T v) {
        value = v;
    }

    public void addChild(String path, @Nonnull T v) {
        if (path.isEmpty()) {
            value = v;
            return;
        }
        if (children == null) {
            children = new TreeMap<>();
        }
        String n = children.ceilingKey(path);
        if (n != null && n.startsWith(path)) {
            if (n.length() == path.length()) {
                children.get(n).addChild("", v);
            } else {
                TrieNode<T> c = children.remove(n);
                TrieNode<T> r = new TrieNode<>(v);
                String suffix = n.substring(path.length());
                r.children = new TreeMap<>();
                r.children.put(suffix, c);
                children.put(path, r);
            }
            return;
        }
        String p = children.lowerKey(path);
        if (p != null && path.startsWith(p)) {
            children.get(p).addChild(path.substring(p.length()), v);
            return;
        }
        children.put(path, new TrieNode<>(v));
    }

    public @Nullable T get(String path) {
        if (path.isEmpty()) return value;
        if (children == null) return null;
        Entry<String, TrieNode<T>> e = children.floorEntry(path);
        if (e != null && path.startsWith(e.getKey())) {
            return e.getValue().get(path.substring(e.getKey().length()));
        }
        return null;
    }
}
