/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.metriks;

/**
 * Implemented by components that handle client-generated metrics.
 * Implementations <strong>must</strong> be thread-safe.
 *
 * <h3>Example Usage:</h3>
 * <pre>
 *     IMetriks metriks = new Metriks(...);
 *     metriks.start();
 *
 *     IMetrik transferMetric = metriks.newMetrik("net.transfer.count");
 *     transferMetric.addField("transport_id", "t");
 *     transferMetric.addField("bytes", 10);
 *     transferMetric.send();
 *
 *     transferTimeMetric = metriks.newMetrik("net.transfer.time")
 *                                 .addField("transport_id", "z")
 *                                 .addField("timeInMillis", 120)
 *                                 .send();
 *
 *     metriks.stop();
 * </pre>
 */
public interface IMetriks
{
    /**
     * Start this component.
     * <p/>
     * The behaviour when {@code start()} is called multiple times is undefined.
     */
    void start();

    /**
     * Stop this component.
     * <p/>
     * The behaviour when {@code stop()} is called multiple times is undefined.
     */
    void stop();

    /**
     * Create a new metrics object.
     *
     *
     * @param topic a dot-separated metric name, for example: "net.connect.time"
     * @return a new {@code IMetrik} instance
     */
    IMetrik newMetrik(String topic);

    /**
     * Representation of a metrics-object returned by an
     * {@link com.aerofs.metriks.IMetriks} implementation.
     * <p/>
     * Implementations <strong>do not</strong> have to be thread-safe.
     */
    public interface IMetrik
    {
        /**
         * Add a field to the metrics object for serialization and delivery.
         *
         * @param fieldName non-namespaced field name (i.e. do not use names of the form a.b.c)
         * @param fieldValue any object that can be serialized into json
         * @return this {@code IMetrik} instance for method chaining
         */
        IMetrik addField(String fieldName, Object fieldValue);

        /**
         * Complete constructing this metric and send it to the remote server.
         * <p/>
         * <strong>No</strong> changes should be made to this object
         * by the caller after {@code send()} is called.
         */
        void send();
    }
}
