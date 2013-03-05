package com.aerofs.tools;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.ex.ExBadArgs;

public class ToolCfgDB implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("    to show values: " + getName() + " get");
            System.out.println("    to set values:  " + getName() + " set <key> <value>");
            return;
        }

        CfgDatabase db = Cfg.db();

        String cmd = args[0];
        if ("get".equals(cmd)) {
            if (args.length != 1) throw new ExBadArgs();

            for (Key key : Key.values()) {
                String value = db.getNullable(key);
                if (value != null && !value.equals(key.defaultValue())) {
                    System.out.println(key.keyString() + " = " + Util.quote(value));
                }
            }

        } else if ("set".equals(cmd)) {
            if (args.length != 3) throw new ExBadArgs();

            Key key = getKeyByName(args[1]);
            if (key == null) throw new ExBadArgs("no key is found");
            db.set(key, args[2]);

        } else if ("rm".equals(cmd) || "remove".equals(cmd)) {
            if (args.length != 2) throw new ExBadArgs();

            Key key = getKeyByName(args[1]);
            if (key == null) throw new ExBadArgs("no key is found");
            db.set(key, key.defaultValue());

        } else if ("dump".equals(cmd)) {
            if (args.length != 2) throw new ExBadArgs();

            File file = new File(args[1]);
            PropWriter w = new PropWriter(new FileOutputStream(file));
            try {
                dump(w, db);
            } finally {
                w.close();
            }

        } else if ("load".equals(cmd)) {
            if (args.length != 2) throw new ExBadArgs();

            Properties props = new Properties();
            File file = new File(args[1]);
            InputStream in = new BufferedInputStream(new FileInputStream(file));
            try {
                props.load(in);
            } finally {
                in.close();
            }

            Map<Key, String> keyMap = new EnumMap<Key, String>(Key.class);
            for (Map.Entry<?, ?> entry : props.entrySet()) {
                Key key = getKeyByName(entry.getKey().toString());
                if (key == null) throw new ExBadArgs("Unknown key: " + entry.getKey());
                String value = (entry.getValue() == null) ? null : entry.getValue().toString();
                keyMap.put(key, value);
            }

            db.set(keyMap);

        } else {
            throw new ExBadArgs("unknown command: " + cmd);
        }
    }

    private @Nullable Key getKeyByName(String name)
    {
        for (Key key : Key.values()) if (key.keyString().equals(name)) return key;
        return null;
    }

    private void dump(PropWriter w, CfgDatabase db) throws IOException
    {
        for (Key key : Key.values()) {
            String value = db.getNullable(key);
            if (value != null && !value.equals(key.defaultValue())) {
                w.writeLine(key.keyString(), value);
            }
        }
    }

    private static class PropWriter implements Flushable, Closeable
    {
        private final BufferedWriter _w;
        private final StringBuilder _sb = new StringBuilder();

        public PropWriter(OutputStream out) throws IOException
        {
            _w = new BufferedWriter(new OutputStreamWriter(out, "ASCII"));
        }

        public void writeLine(String key, String value) throws IOException
        {
            escape(_sb, key, true, true);
            _sb.append(" = ");
            escape(_sb, value, false, true);
            _w.append(_sb);
            _sb.setLength(0);
            _w.newLine();
        }

        @Override
        public void flush() throws IOException
        {
            _w.flush();
        }

        @Override
        public void close() throws IOException
        {
            _w.close();
        }

        private static void escape(Appendable a, String str, boolean escapeSpace, boolean escapeUnicode)
                throws IOException
        {
            int len = str.length();

            for (int i = 0; i < len; ++i) {
                char c = str.charAt(i);
                if (c > 0x3d && c < 0x7f) {
                    if (c == '\\') {
                        a.append('\\').append('\\');
                    } else {
                        a.append(c);
                    }
                } else {
                    switch (c) {
                    case ' ':
                        if (i == 0 || escapeSpace) a.append('\\');
                        a.append(' ');
                        break;
                    case '\t': a.append('\\').append('t'); break;
                    case '\n': a.append('\\').append('n'); break;
                    case '\r': a.append('\\').append('r'); break;
                    case '\f': a.append('\\').append('f'); break;
                    case '=':
                    case ':':
                    case '#':
                    case '!':
                        a.append('\\').append(c);
                        break;
                    default:
                        if ((c < 0x20 || c > 0x7e) & escapeUnicode) {
                            a.append('\\').append('u')
                            .append(hex(c >> 12))
                            .append(hex(c >> 8))
                            .append(hex(c >> 4))
                            .append(hex(c >> 0));
                        } else {
                            a.append(c);
                        }
                    }
                }
            }
        }

        private static char hex(int v) {
            return hexDigits[v & 0xf];
        }
        private static final char[] hexDigits = {
            '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
        };

    }

    @Override
    public String getName()
    {
        return "cfg";
    }
}
