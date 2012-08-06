package com.aerofs.daemon.core.tc;

/**
 *  Category
 */
public enum Cat {

    CLIENT("CLT"),
    SERVER("SRV"),
    HOUSEKEEPING("HK"),
    UNLIMITED("UN"),
    DID2USER("D2U");

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
