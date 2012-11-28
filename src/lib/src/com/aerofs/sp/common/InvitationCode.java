package com.aerofs.sp.common;

import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;

/**
 * This class defines the permissible types of Invitation Codes for AeroFS,
 * and provides static methods to
 * - generate specified types of codes (i.e. generate a string representation of the code)
 * - get the type of a given code (from string format)
 *
 * Code types are distinguished by i) string length, and ii) the suffix character. For
 * example, all batch signup codes will end with the same character,
 * 'a'.  The suffix character is determined according to the ordinal of the code type.
 */
public class InvitationCode
{
    /**
     * The different types of Invitation codes
     * N.B. reordering these will cause codes generated from previous revisions to fail the
     *      getType method, as the suffix character is determined from the enum ordinal.
     */
    public static enum CodeType
    {
        // We pick suffix chars arbitrarily
        TARGETED_SIGNUP(8, 'b'),
        SHARE_FOLDER(8, 'c'),
        INVALID(0, '\0');

        // Number of characters for the code type (i.e. length of the string)
        private final int _length;
        private final char _suffix;

        private CodeType(int length, char suffix)
        {
            _length = length;
            _suffix = suffix;
        }
    }

    private static final Logger l = Util.l(InvitationCode.class);

    public static String generate(CodeType codeType)
    {
        // Ensure the given codeType is supported
        switch (codeType) {
        case TARGETED_SIGNUP:
        case SHARE_FOLDER:
            // These are supported
            break;
        default:
            l.error("Code type " + codeType + " unsupported.");
            return null;
        }

        // Generate the random code and add a type-specific suffix
        // - assume the codeType ordinals do not exceed BASE62_CHARS.length.
        // - taking the modulo would be safer, but probably unnecessary
        return Base62CodeGenerator.newRandomBase62String(codeType._length - 1)
                + Base62CodeGenerator.BASE62_CHARS[codeType.ordinal()];
    }

    public static CodeType getType(@Nullable String code)
    {
        if (code == null) {
            return CodeType.INVALID;
        } else if (matchesType(code, CodeType.SHARE_FOLDER)) {
            return CodeType.SHARE_FOLDER;
        } else if (matchesType(code, CodeType.TARGETED_SIGNUP)) {
            return CodeType.TARGETED_SIGNUP;
        } else {
            return CodeType.INVALID;
        }
    }

    /**
     * @return true if the suffix and the length of a code match those expected from a given type
     */
    private static boolean matchesType(String code, CodeType expected)
    {
        int length = code.length();
        char suffix = code.charAt(length - 1);
        return suffix == expected._suffix && length == expected._length;
    }
}
