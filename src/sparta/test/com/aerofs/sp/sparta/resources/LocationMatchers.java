package com.aerofs.sp.sparta.resources;

import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.matchers.CapturingMatcher;

/**
 * an utility class for matching the location header of a http response with a field in the
 * response body.
 */
public class LocationMatchers
{
    private final String _prefix;
    public final CapturingMatcher<String> _locationMatcher;
    public final Matcher<String> _keyMatcher;

    public LocationMatchers(String prefix)
    {
        _prefix = prefix;
        _locationMatcher = new CapturingMatcher<String>() {
            @Override
            public boolean matches(Object argument)
            {
                if (argument instanceof String && ((String)argument).startsWith(_prefix)) {
                    captureFrom(((String)argument).substring(_prefix.length()));
                    return true;
                }
                return false;
            }
        };
        _keyMatcher = new ArgumentMatcher<String>()
        {
            @Override
            public boolean matches(Object argument)
            {
                return _locationMatcher.getLastValue() != null
                        && _locationMatcher.getLastValue().equals(argument);
            }
        };
    }
}
