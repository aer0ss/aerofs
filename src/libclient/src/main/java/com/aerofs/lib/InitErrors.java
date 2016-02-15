package com.aerofs.lib;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

// The purpose of this class is to track any error encountered during the early stage of
// initialization (Main.java in particular) and propagate those information across module
// boundary (from daemon to gui). This class is used much like com.aerofs.lib.Approot.
public class InitErrors
{
    private static final Logger l = Loggers.getLogger(InitErrors.class);

    private static String _title = null;
    private static String _description = null;

    public static void setErrorMessage(String title, String description)
    {
        checkNotNull(title);
        checkNotNull(description);

        l.error("Encountered an init error: {}\n{}", title, description);

        _title = title;
        _description = description;
    }

    public static boolean hasErrorMessages()
    {
        return _title != null;
    }

    public static String getTitle()
    {
        checkNotNull(_title);
        return _title;
    }

    public static String getDescription()
    {
        checkNotNull(_description);
        return _description;
    }
}
