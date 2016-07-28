/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.config;

import com.aerofs.baseline.ServiceConfiguration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.logging.ConsoleLoggingConfiguration;
import com.aerofs.baseline.logging.FileLoggingConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.io.Resources;
import org.hamcrest.Matchers;
import org.hibernate.validator.HibernateValidator;
import org.junit.Test;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;

public final class TestConfiguration {

    /**
     * Helper class that uses a subclass of {@code Configuration} as a type argument.
     * This allows our Generics utility methods to determine the actual class type and
     * construct it.
     * <p/>
     * See http://gafter.blogspot.com/2006/12/super-type-tokens.html
     */
    @SuppressWarnings("unused") // parameter 'T' is unused
    public abstract static class ConfigurationHolder<T extends Configuration> {

        // this space intentionally left blank
    }

    private final ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class).configure().buildValidatorFactory();

    @Test(expected = JsonMappingException.class)
    public void shouldThrowIfParsingInvalidConfigFile() throws URISyntaxException, IOException {
        ConfigurationHolder<ServiceConfiguration> holder = new ConfigurationHolder<ServiceConfiguration>() {};
        loadConfiguration(holder, "config_tests/invalid_config.yml");
    }

    @Test(expected = ConstraintViolationException.class)
    public void shouldThrowIfValidatingInvalidConfigFile() throws URISyntaxException, IOException {
        ConfigurationHolder<ServiceConfiguration> holder = new ConfigurationHolder<ServiceConfiguration>() {};
        Configuration loaded = loadConfiguration(holder, "config_tests/missing_admin_block_config.yml");
        Configuration.validateConfiguration(validatorFactory.getValidator(), loaded);
    }

    @Test
    public void shouldLoadValidConfiguration() throws URISyntaxException, IOException {
        HttpConfiguration admin = new HttpConfiguration();
        admin.setHost("localhost");
        admin.setPort((short) 8888);

        HttpConfiguration service = new HttpConfiguration();
        service.setHost("0.0.0.0");
        service.setPort((short) 9999);

        ConsoleLoggingConfiguration console = new ConsoleLoggingConfiguration();
        console.setEnabled(true);

        FileLoggingConfiguration file = new FileLoggingConfiguration();
        file.setEnabled(true);
        file.setLogfile("/var/log/baseline/baseline.log");

        LoggingConfiguration logging = new LoggingConfiguration();
        logging.setLevel("DEBUG");
        logging.setConsole(console);
        logging.setFile(file);

        ServiceConfiguration actual = new ServiceConfiguration();
        actual.setAdmin(admin);
        actual.setService(service);
        actual.setLogging(logging);

        ConfigurationHolder<ServiceConfiguration> holder = new ConfigurationHolder<ServiceConfiguration>() {};
        Configuration loaded = loadConfiguration(holder, "config_tests/valid_config.yml");
        Configuration.validateConfiguration(validatorFactory.getValidator(), loaded);

        assertThat(loaded, Matchers.<Configuration>equalTo(actual));
    }

    private static <T extends Configuration> T loadConfiguration(Object typedClass, String filename) throws IOException, URISyntaxException {
        URL resourceURL = Resources.getResource(filename);

        try (FileInputStream in = new FileInputStream(new File(resourceURL.toURI()))) {
            return Configuration.loadYAMLConfigurationFromStream(typedClass.getClass(), in);
        }
    }
}
