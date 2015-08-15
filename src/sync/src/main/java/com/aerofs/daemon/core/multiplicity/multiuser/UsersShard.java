package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.ids.UserID;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UsersShard {

    private static final Logger l = LoggerFactory.getLogger(UsersShard.class);
    private final CsvFilter csvFilter;
    private final RegexFilter regexFilter;

    @Inject
    public UsersShard(CsvFilter usersCvsGetter, RegexFilter regexFilter) {
        this.csvFilter = usersCvsGetter;
        this.regexFilter = regexFilter;
    }

    // This method returns true if the user exists in users.csv or matches the
    // pattern specified in regex.txt. Therefore, the user list constructed is
    // the set union of the matches to the specified regex pattern in regex.txt and
    // the existing users.csv.
    // eg. [a-zA-Z].* can be used as a pattern to filter in everything whose first
    // letter is A-Z, case insensitive.
    public boolean contains(UserID user) {
        l.info("{}", user);
        //If there is no regex.txt and users.csv is empty, return true.
        if (csvFilter.get().isEmpty() && regexFilter.getPattern() == null) return true;

        if (csvFilter.get().contains(user)) {
            l.info("csv match");
            return true;
        }

        if (regexFilter.getPattern() != null && regexFilter.getPattern().matcher(user.getString()).matches()) {
            l.info("regex match");
            return true;
        }

        l.info("no match");
        return false;
    }

    public boolean isSharded() {
        return !(csvFilter.get().isEmpty() && regexFilter.getPattern() == null);
    }
}
