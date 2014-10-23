package com.aerofs.overload.driver;

import com.aerofs.overload.HttpRequestProvider;
import com.aerofs.overload.LoadGenerator;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public abstract class OverloadDriver {

    private static final int NO_OPTIONS_SPECIFIED_EXIT_CODE = 1;
    private static final int STATS_LOGGING_INITIAL_DELAY = 0;
    private static final int STATS_LOGGING_PERIOD = 10000;

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final Timer timer = new Timer();

    @SuppressWarnings("AccessStaticViaInstance")
    public final void run(String[] args) {
        try {
            Options options = new Options();

            // add the default options
            options.addOption(OptionBuilder.hasArg().isRequired().withLongOpt("host").withDescription("hostname of the server").create("h"));
            options.addOption(OptionBuilder.hasArg().isRequired().withLongOpt("port").withDescription("port on which the server listens").create("p"));
            options.addOption(OptionBuilder.hasArg().isRequired().withLongOpt("connections").withDescription("number of concurrent http connections").create("c"));
            options.addOption(OptionBuilder.hasArg().isRequired().withLongOpt("rate").withDescription("target request rate per second").create("r"));
            options.addOption(OptionBuilder.hasArg().withLongOpt("runtime").withDescription("amount of time in milliseconds for which the load generator will run").create("t"));

            // allow specializations to add their own options
            addCommandLineOptions(options);

            // check if the user specified *any* options
            if (args.length == 0) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp(getClass().getSimpleName(), options, true);
                System.exit(NO_OPTIONS_SPECIFIED_EXIT_CODE);
            }

            // parse the arguments
            CommandLineParser parser = new BasicParser();
            CommandLine commandLine = parser.parse(options, args, true);

            // get the arguments we care about
            int connectionCount = Integer.parseInt(commandLine.getOptionValue("connections"));
            int targetRequestRate = Integer.parseInt(commandLine.getOptionValue("rate"));
            String host = commandLine.getOptionValue("host");
            int port = Integer.parseInt(commandLine.getOptionValue("port"));

            // create the requester
            HttpRequestProvider requestProvider = newConfiguredRequestProvider(commandLine);
            final LoadGenerator generator = new LoadGenerator(host, port, requestProvider, connectionCount, targetRequestRate);

            // add a runtime shutdown hook
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    terminate(generator);
                }
            });

            // if the user specified a runtime, shutdown the client at that time
            if (commandLine.hasOption("runtime")) {
                int runtime = Integer.parseInt(commandLine.getOptionValue("runtime"));
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        terminate(generator);
                    }
                }, runtime);
            }

            // print stats at a fixed interval
            timer.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    generator.logStats();
                }
            }, STATS_LOGGING_INITIAL_DELAY, STATS_LOGGING_PERIOD);

            // finally, start the requester
            try {
                generator.start();
            } finally {
                terminate(generator);
            }
        } catch (ParseException e) {
            LOGGER.error("fail parse command line", e);
        } catch (Exception e) {
            LOGGER.error("fail run client", e);
        }
    }

    protected void addCommandLineOptions(Options options) {
        // noop
    }

    protected abstract HttpRequestProvider newConfiguredRequestProvider(CommandLine commandLine);

    private void terminate(LoadGenerator generator) {
        generator.shutdown();
        generator.logStats();
        timer.cancel(); // OK to be called in the context of an executing task
    }
}
