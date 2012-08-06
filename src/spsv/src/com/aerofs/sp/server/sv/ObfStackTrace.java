package com.aerofs.sp.server.sv;

/**
 * An obfuscated stack trace, used by SVReactor for hashing the obfuscated
 * stack traces to an unobfuscated stack trace.
 *
 * Since proguard changes the obfuscation map from version to version, we need to store
 * the corresponding version as well as the stack trace.
 *
 */
public class ObfStackTrace {

    String _stackTrace;
    String _version;

    public ObfStackTrace(String stackTrace, String version) {
        _stackTrace = stackTrace;
        _version = version;
    }
    @Override
    public int hashCode() {
        return _stackTrace.hashCode() + _version.hashCode();
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) return true;
        if (!(other instanceof ObfStackTrace)) return false;

        ObfStackTrace st2 = (ObfStackTrace)other;
        return _stackTrace.equals(st2._stackTrace) && _version.equals(st2._version);
    }

}
