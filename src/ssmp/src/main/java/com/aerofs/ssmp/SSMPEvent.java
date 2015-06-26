package com.aerofs.ssmp;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SSMPEvent {
    final static int NO_FIELD = 0;
    final static int FIELD_TO = 1;
    final static int FIELD_PAYLOAD = 2;

    public enum Type {
        SUBSCRIBE("SUBSCRIBE", FIELD_TO),
        UNSUBSCRIBE("UNSUBSCRIBE", FIELD_TO),
        UCAST("UCAST", FIELD_PAYLOAD),
        MCAST("MCAST", FIELD_TO | FIELD_PAYLOAD),
        BCAST("BCAST", FIELD_PAYLOAD),
        PING("PING", NO_FIELD),
        PONG("PONG", NO_FIELD),
        ;

        final byte[] _s;
        final int _fields;

        private final static Map<String, Type> _m;
        static {
            _m = new ConcurrentHashMap<>();
            for (Type t : values()) {
                _m.put(t.name(), t);
            }
        }

        // TODO: use trie instead?
        static Type byName(byte[] n) {
            return _m.get(new String(n, StandardCharsets.US_ASCII));
        }

        Type(String s, int fields) {
            _s = s.getBytes(StandardCharsets.US_ASCII);
            _fields = fields;
        }
    }

    public final SSMPIdentifier from;
    public final Type type;
    public final @Nullable SSMPIdentifier to;
    public final @Nullable String payload;

    public SSMPEvent(SSMPIdentifier from, Type type, @Nullable SSMPIdentifier to, @Nullable String payload) {
        this.from = from;
        this.type = type;
        this.to = to;
        this.payload = payload;
    }
}
