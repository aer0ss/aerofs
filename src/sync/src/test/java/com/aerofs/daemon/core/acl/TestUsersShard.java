package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.multiplicity.multiuser.RegexFilter;
import com.aerofs.daemon.core.multiplicity.multiuser.CsvFilter;
import com.aerofs.daemon.core.multiplicity.multiuser.UsersShard;
import com.aerofs.ids.UserID;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestUsersShard {

    private final CsvFilter csvFilter = mock(CsvFilter.class);
    private final RegexFilter regexFilter = mock(RegexFilter.class);
    private final UsersShard usersShard = new UsersShard(csvFilter, regexFilter);

    @Test
    public void shouldAcceptUserWhenNoShardingIsUsed() throws Exception {

        when(csvFilter.get()). thenReturn(ImmutableList.of());
        when(regexFilter.getPattern()).thenReturn(null);

        assertTrue(usersShard.contains(UserID.fromExternal("alpha")));

        verify(csvFilter).get();
        verify(regexFilter).getPattern();
    }

    @Test
    public void shouldAcceptUserWhenUsersCvsExistsAndRegexTextIsNull() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of
                (UserID.fromExternal("alpha"), UserID.fromExternal("lambda"), UserID.fromExternal("zeta")));
        when(regexFilter.getPattern()).thenReturn(null);

        assertTrue(usersShard.contains(UserID.fromExternal("alpha")));

        verify(csvFilter, atLeastOnce()).get();
    }

    @Test
    public void shouldNotAcceptUserWhenUsersCvsExistsAndRegexTextIsNull() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of
                (UserID.fromExternal("alpha"), UserID.fromExternal("lambda"), UserID.fromExternal("zeta")));
        when(regexFilter.getPattern()).thenReturn(null);

        assertFalse(usersShard.contains(UserID.fromExternal("beta")));

        verify(csvFilter, atLeastOnce()).get();
    }

    @Test
    public void shouldAcceptUserWhenUsersCvsExistsAndRegexTextIsEmpty() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of
                (UserID.fromExternal("alpha"), UserID.fromExternal("lambda"), UserID.fromExternal("zeta")));
        when(regexFilter.getPattern()).thenReturn(Pattern.compile(""));

        assertTrue(usersShard.contains(UserID.fromExternal("alpha")));

        verify(csvFilter, atLeastOnce()).get();
    }

    @Test
    public void shouldNotAcceptUserWhenUsersCvsExistsAndRegexTextIsEmpty() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of
                (UserID.fromExternal("alpha"), UserID.fromExternal("lambda"), UserID.fromExternal("zeta")));
        when(regexFilter.getPattern()).thenReturn(Pattern.compile(""));

        assertFalse(usersShard.contains(UserID.fromExternal("beta")));

        verify(csvFilter, atLeastOnce()).get();
    }

    @Test
    public void shouldAcceptUserWhenRegexMatches() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of());
        when(regexFilter.getPattern()).thenReturn(Pattern.compile("[a-kA-K].*"));

        assertTrue(usersShard.contains(UserID.fromExternal("alpha")));
        assertFalse(usersShard.contains(UserID.fromExternal("zeta")));

        verify(regexFilter, atLeastOnce()).getPattern();
    }

    @Test
    public void shouldAcceptUserWhenUsersCsvExistsRegexMatches() throws Exception {
        when(csvFilter.get()).thenReturn(ImmutableList.of
                (UserID.fromExternal("alpha"), UserID.fromExternal("lambda"), UserID.fromExternal("zeta")));
        when(regexFilter.getPattern()).thenReturn(Pattern.compile("[a-kA-K].*"));

        assertTrue(usersShard.contains(UserID.fromExternal("alpha")));
        assertTrue(usersShard.contains(UserID.fromExternal("zeta")));
        assertFalse(usersShard.contains(UserID.fromExternal("phi")));

        verify(regexFilter, atLeastOnce()).getPattern();
    }
}