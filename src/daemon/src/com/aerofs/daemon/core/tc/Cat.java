package com.aerofs.daemon.core.tc;

/**
 *  Category
 */
public enum Cat {

    // TODO: avoid tying up core thread on I/O (use async processing or coroutines)
    CLIENT("CLT"),
    SERVER("SRV"),
    API_UPLOAD("UPLOAD"),
    HOUSEKEEPING("HK"),
    UNLIMITED("UN"),
    RESOLVE_USER_ID("RESOLVE");

    private final String _name;

    Cat(String name)
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

}
