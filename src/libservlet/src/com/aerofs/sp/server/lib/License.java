package com.aerofs.sp.server.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.google.common.base.Optional;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getOptionalStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/*
 * License related functions. See license.py for its counterpart in Python.
 */
public class License
{
    // 0 if the license is not present or the format is not supported.
    long _expireTimestamp;
    int _seats;

    public License()
    {
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) initForPrivateDeployment();
        else initForPublicDeployment();
    }

    private void initForPrivateDeployment()
    {
        Optional<String> type = getOptionalStringProperty("license_type");

        // We only accept normal licenses for the time being
        if (!type.isPresent() || !type.get().equals("normal")) return;

        String validUntil = getStringProperty("license_valid_until", "");

        try {
            _expireTimestamp = new SimpleDateFormat("yyyy-MM-dd").parse(validUntil).getTime();
        } catch (ParseException e) {
            Loggers.getLogger(License.class).warn("invalid license_valid_until: {}", validUntil);
            // leave _expireTimestamp as zero
        }

        _seats = getIntegerProperty("license_seats", 0);
    }

    private void initForPublicDeployment()
    {
        _expireTimestamp = Long.MAX_VALUE;
        _seats = Integer.MAX_VALUE;
    }

    /**
     * Return whether the license exists and has not expired
     *
     * It may be called very frequently (by SPServlet). To optimize, we cache the computed timestamp
     * rather than querying the configuration system every time the method is called.
     */
    public boolean isValid()
    {
        // N.B. keep the comparison consistent with license.py:is_license_present_and_valid()
        return System.currentTimeMillis() <= _expireTimestamp;
    }

    /**
     * Return the number of seats this license allows.
     * N.B. this method does not check the license's validity, test that first with isValid().
     */
    public int seats()
    {
        return _seats;
    }

}
