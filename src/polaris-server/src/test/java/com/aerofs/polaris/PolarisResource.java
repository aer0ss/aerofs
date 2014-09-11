package com.aerofs.polaris;

import com.aerofs.baseline.metrics.MetricRegistries;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import org.junit.rules.ExternalResource;

 public final class PolarisResource extends ExternalResource {

     private final Polaris polaris = new Polaris();

     @Override
     protected void before() throws Throwable {
         polaris.runWithConfiguration(TestStatics.POLARIS_CONFIGURATION);
     }

     @Override
     protected void after() {
         // remove all registered metrics
         MetricRegistry registry = MetricRegistries.getRegistry();
         registry.removeMatching(new MetricFilter() {
             @Override
             public boolean matches(String name, Metric metric) {
                 return true;
             }
         });

         // shutdown the server
         polaris.shutdown();
     }
 }
