/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.common;

import com.aerofs.base.BaseSecUtil;

/**
 * Base62CodeGeneraor is use by the invitation mailer and by the password reset mailer.
 * It generates securely random tokens.
 */
public class Base62CodeGenerator
{
    private final static int CODE_LENGTH = 8;

    public static String generate()
    {
        return newRandomBase62String(CODE_LENGTH);
    }

    private static String newRandomBase62String(int len)
    {
        assert len > 0;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int rand = BaseSecUtil.newRandomInt() % BASE62_CHARS.length;
            sb.append(BASE62_CHARS[rand >= 0 ? rand : rand + BASE62_CHARS.length]);
        }
        return sb.toString();
    }

    private static final char[] BASE62_CHARS;
    static {
        BASE62_CHARS = new char[62];
        int idx = 0;
        for (char i = 'a'; i <= 'z'; i++) BASE62_CHARS[idx++] = i;
        for (char i = 'A'; i <= 'Z'; i++) BASE62_CHARS[idx++] = i;
        for (char i = '0'; i <= '9'; i++) BASE62_CHARS[idx++] = i;
        assert idx == BASE62_CHARS.length;
    }
}
