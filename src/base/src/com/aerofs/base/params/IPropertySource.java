/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.params;

import java.net.InetSocketAddress;
import java.net.URL;
import java.security.cert.X509Certificate;

/**
 * Problem: we can't use the dynamic property system on Android because it's too heavy. (Android has
 * a limit of 64k methods that you can have in an apk, and the dynamic property system was blowing
 * up that limit because of its dependency on the Apache configuration system.)
 *
 * Therefore, we need a way for the code in base to use either the dynamic property system on the
 * regular clients and servers, or a simpler system on Android.
 *
 * This interface achieves this goal. It creates a façade that defines a "property source". This
 * can be either the dynamic property system, or a simpler system on Android.
 *
 * Note: down the road, since we don't envision to use the "dynamic" feature of the dynamic property
 * system, I would suggest switching for a simpler implementation based on Java Properties
 * (http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html)
 *
 * This would allow code reuse between Android and the other clients, as well as simplify the overall
 * architecture.
 */
public interface IPropertySource
{
    public IProperty<String> stringProperty(String key, String defaultValue);
    public IProperty<InetSocketAddress> addressProperty(String key, InetSocketAddress defaultValue);
    public IProperty<URL> urlProperty(String key, String defaultValue);
    public IProperty<X509Certificate> certificateProperty(String key, X509Certificate defaultValue);
}
