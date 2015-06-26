package com.aerofs.ssmp;

import org.junit.Assert;
import org.junit.Test;


public class TestTrieNode {

    @Test
    public void shouldNotMatchEmpty() {
        TrieNode<Boolean> t = new TrieNode<>();

        Assert.assertNull(t.get("foo"));
    }

    @Test
    public void shouldMatchSingleton() {
        TrieNode<Boolean> t = new TrieNode<>();
        t.addChild("foo", true);

        Assert.assertEquals(true, t.get("foo"));
    }

    @Test
    public void shouldNotMatchSingleton() {
        TrieNode<Boolean> t = new TrieNode<>();
        t.addChild("foo", true);

        Assert.assertNull(t.get("f"));
        Assert.assertNull(t.get("fool"));
        Assert.assertNull(t.get("bar"));
    }

    @Test
    public void shouldInsert() {
        TrieNode<Integer> t = new TrieNode<>();
        t.addChild("foo", 1);
        t.addChild("four", 2);
        t.addChild("foolish", 3);
        t.addChild("fail", 4);
        t.addChild("bar", 5);
        t.addChild("fourteen", 6);

        Assert.assertEquals((Integer)1, t.get("foo"));
        Assert.assertEquals((Integer)2, t.get("four"));
        Assert.assertEquals((Integer)3, t.get("foolish"));
        Assert.assertEquals((Integer)4, t.get("fail"));
        Assert.assertEquals((Integer)5, t.get("bar"));
        Assert.assertEquals((Integer)6, t.get("fourteen"));
        Assert.assertNull(t.get("f"));
        Assert.assertNull(t.get("fool"));
        Assert.assertNull(t.get("qux"));
        Assert.assertNull(t.get("bartender"));
    }
}
