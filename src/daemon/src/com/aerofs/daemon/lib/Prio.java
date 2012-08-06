package com.aerofs.daemon.lib;

public enum Prio {
    // the smaller the cardinal, the higher the priority
    HI("HI"),
    LO("LO");

    private final String _name;

    Prio(String name)
    {
        _name = name;
    }

    /**
     * Cannot use class names due to obfuscation
     */
    @Override
    public String toString()
    {
        return _name;
    }

    public static Prio higher(Prio p1, Prio p2)
    {
        return p1.compareTo(p2) < 0 ? p1 : p2;
    }
}
