/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.metrics;

import com.aerofs.base.Loggers;
import com.aerofs.lib.rocklog.RockLog;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Metered;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricPredicate;
import com.yammer.metrics.core.MetricProcessor;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Summarizable;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.VirtualMachineMetrics;
import com.yammer.metrics.reporting.AbstractPollingReporter;
import com.yammer.metrics.stats.Snapshot;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class RockLogReporter extends AbstractPollingReporter implements MetricProcessor<Long>
{
    private static final Logger l = Loggers.getLogger(RockLogReporter.class);

    private final VirtualMachineMetrics _vm;
    private @Nullable volatile com.aerofs.lib.rocklog.Metrics _rocklogMetrics; // current metric being populated

    public static void enable(long period, TimeUnit unit)
    {
        // NOTE: don't need to keep reporter around because our parent starts a background thread
        // that holds a reference to the reporter object

        RockLogReporter reporter = new RockLogReporter(VirtualMachineMetrics.getInstance(), Metrics.defaultRegistry(), "rocklog");
        reporter.start(period, unit);
    }

    protected RockLogReporter(VirtualMachineMetrics vmMetrics, MetricsRegistry registry, String name)
    {
        super(registry, name);
        this._vm = vmMetrics;
    }

    @Override
    public void run()
    {
        try {
            _rocklogMetrics = RockLog.newMetrics();

            addApplicationMetrics_();
            addVirtualMachineMetrics_();

            checkNotNull(_rocklogMetrics);  // won't be null, but here to silence IDE warning

            _rocklogMetrics.send();
        } catch (Exception e) {
            l.error("fail send metrics err:" + e);
        } finally {
            _rocklogMetrics = null;
        }
    }

    //
    // the process* methods are called by the Metrics implementations (Meter, Counter, Gauge, etc.)
    // essentially, they pass in the data that they have and the reporter's responsibility is to
    // format it in a way that's understood by the backend
    //

    @Override
    public void processCounter(MetricName metricName, Counter counter, Long timestamp)
            throws Exception
    {
        String name = metricNameToString_(metricName);
        addLong_(name, "count", counter.count());
    }

    @Override
    public void processMeter(MetricName metricName, Metered metered, Long value)
            throws Exception
    {
        String name = metricNameToString_(metricName);
        addMeter_(name, metered);
    }

    @Override
    public void processHistogram(MetricName metricName, Histogram histogram, Long timestamp)
            throws Exception
    {
        String name = metricNameToString_(metricName);
        addSummarizable_(name, histogram);
        addSampling_(name, histogram);
    }

    @Override
    public void processTimer(MetricName metricName, Timer timer, Long timestamp)
            throws Exception
    {
        String name = metricNameToString_(metricName);
        addSummarizable_(name, timer);
        addSampling_(name, timer);
    }

    @Override
    public void processGauge(MetricName metricName, Gauge<?> gauge, Long timestamp)
            throws Exception
    {
        String name = metricNameToString_(metricName);
        addObject_(name, gauge);
    }

    private String metricNameToString_(MetricName metricName)
    {
        StringBuilder sb = new StringBuilder()
                .append(metricName.getGroup())
                .append('.')
                .append(metricName.getType())
                .append('.');
        if (metricName.hasScope()) {
            sb.append(metricName.getScope()).append('.');
        }
        return sb.append(metricName.getName()).toString();
    }

    private void addMeter_(String metricName, Metered metered)
    {
        addLong_(metricName, "count", metered.count());
        addFloat_(metricName, "meanRate", metered.meanRate());
        addFloat_(metricName, "1MinuteRate", metered.oneMinuteRate());
        addFloat_(metricName, "5MinuteRate", metered.fiveMinuteRate());
        addFloat_(metricName, "15MinuteRate", metered.fifteenMinuteRate());
    }

    private void addSummarizable_(String metricName, Summarizable summarizable)
    {
        addFloat_(metricName, "min", summarizable.min());
        addFloat_(metricName, "max", summarizable.max());
        addFloat_(metricName, "mean", summarizable.mean());
        addFloat_(metricName, "stddev", summarizable.stdDev());
    }

    private void addSampling_(String metricName, Sampling sampling)
    {
        final Snapshot snapshot = sampling.getSnapshot();
        addFloat_(metricName, "median", snapshot.getMedian());
        addFloat_(metricName, "75percentile", snapshot.get75thPercentile());
        addFloat_(metricName, "95percentile", snapshot.get95thPercentile());
        addFloat_(metricName, "98percentile", snapshot.get98thPercentile());
        addFloat_(metricName, "99percentile", snapshot.get99thPercentile());
        addFloat_(metricName, "999percentile", snapshot.get999thPercentile());
    }

    private void addLong_(String metricName, String component, long value)
    {
        checkNotNull(_rocklogMetrics);

        String key = metricName + "." + component;
        _rocklogMetrics.addMetric(key, value);
    }

    private void addFloat_(String metricName, String component, double value)
    {
        String fullMetricName = metricName + "." + component;

        if (Double.isNaN(value)) {
            l.warn("nan m:" + fullMetricName + " v:" + value);
            RockLog.newDefect("rocklog.conversion.nan").addData("metric", fullMetricName).sendAsync();
            return;
        }

        checkNotNull(_rocklogMetrics);

        _rocklogMetrics.addMetric(fullMetricName, value);
    }

    private void addObject_(String key, Gauge<?> gauge)
    {
        checkNotNull(_rocklogMetrics);

        _rocklogMetrics.addMetric(key, String.format("%s", gauge));
    }

    private void addApplicationMetrics_()
            throws Exception
    {
        SortedMap<String, SortedMap<MetricName, Metric>> allMetrics = getMetricsRegistry().groupedMetrics(MetricPredicate.ALL);
        for (Map.Entry<String, SortedMap<MetricName, Metric>> groupedEntry : allMetrics.entrySet()) {

            SortedMap<MetricName, Metric> groupedMetrics = groupedEntry.getValue();
            for (Map.Entry<MetricName, Metric> singleEntry : groupedMetrics.entrySet()) {

                MetricName name = singleEntry.getKey();
                Metric metric = singleEntry.getValue();
                if (metric != null) {
                    metric.processWith(this, name, System.currentTimeMillis());
                }
            }
        }
    }

    private void addVirtualMachineMetrics_()
    {
        addFloat_("jvm.memory", "heap_usage", _vm.heapUsage());
        addFloat_("jvm.memory", "non_heap_usage", _vm.nonHeapUsage());
        for (Map.Entry<String, Double> pool : _vm.memoryPoolUsage().entrySet()) {
            addFloat_("jvm.memory.memory_pool_usages", pool.getKey(), pool.getValue());
        }

        addLong_("jvm", "daemon_thread_count", _vm.daemonThreadCount());
        addLong_("jvm", "thread_count", _vm.threadCount());
        addLong_("jvm", "uptime", _vm.uptime());

        if (!isWindows()) {
            addFloat_("jvm", "fd_usage", _vm.fileDescriptorUsage());
        }

        for (Map.Entry<Thread.State, Double> entry : _vm.threadStatePercentages().entrySet()) {
            addFloat_("jvm.thread-states", entry.getKey().toString().toLowerCase(), entry.getValue());
        }

        for (Map.Entry<String, VirtualMachineMetrics.GarbageCollectorStats> entry : _vm.garbageCollectors().entrySet()) {
            final String name = "jvm.gc." + entry.getKey();
            addLong_(name, "time", entry.getValue().getTime(TimeUnit.MILLISECONDS));
            addLong_(name, "runs", entry.getValue().getRuns());
        }
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
