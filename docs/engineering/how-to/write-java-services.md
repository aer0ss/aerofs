Building Java Micro Services: Recommendations
---

*Credits to Allen for the initial version*


The trend in the last decade has been a shift towards micro-services. At a high level each micro-service:

1. Provides a well-defined API to a resource
2. Is the single point-of-contact for that resource
3. Relies on external tools for process and package management

Tomcat is an *application container* that attempts to handle 3. itself instead of delegating responsibility to better tools. It comes from a world where a single tomcat process would contain multiple embedded applications.

At AeroFS we prefer to use micro-services because they:

- Impose greater design discipline
- Are easier to reason about
- Are easier to package and manage
- Are simpler than a web-application inside tomcat

Here are my recommendations when building Java micro-services. I'm going to structure it in the form "I want to" followed by my recommendation. **I want to**:

**Build a non-REST service/client**

Use netty-3.x. There's absolutely no reason not to. We have a huge number of handlers and institutional knowledge around this stack. If you have problems, someone (Hugues, Jon and I) has probably hit it and solved it.

**Build a simple HTTP service that consumes HTTP verbs at a single route**

Use Jetty. Jetty is an embeddable servlet container that allows you to build an HTTP-consuming micro-service quickly. Since it supports the servlet spec you can build to that spec (which isn't bad, frankly) or easily migrate a service built on top of Tomcat.

**Build a REST service with custom routes**

Use restless or baseline. (Both micro-frameworks are JAX-RS-compliant under the hood.) I recommend using restless if you need to support mutual authentication. I prefer using baseline in the services I build because it handles component wiring out-of-the-box, has well-integrated metrics and database pooling/query library support.

**Use an ORM**

Don't.

**Use a database**

Use MySQL. This is our internal standard - we ship it, have a lot of institutional knowledge around it, and it is well-supported. The use of Redis in persistent mode is *deprecated*.

**Use a hot cache**

Use Redis. Redis is the gold standard for an in-memory key-value store. It has a huge ecosystem, excellent documentation and is well-supported.

**Make SQL queries**

Use JDBI. I've used JDBI multiple times in anger and I highly recommend it (specifically their "fluent interfaces"). It hides the boilerplate of JDBC and includes proper transaction and resource management. It strikes the right balance between abstraction and control for a wide range of use cases. Do not use it if you need to do something incredibly custom or want to represent/handle complex table relations.

**Perform DB migrations**

Use Flyway. Again, there's a lot of institutional knowledge around using Flyway and I've heard nothing but good things :)

**Log**

Use slf4j/logback. If your library uses other logging frameworks find the appropriate slf4j-* adapter. By including this library in your classpath all log statements will be transparently routed through slf4j into logback. Use the template logback.xml in the source code.

**Represent time**

Use UTC. All our servers and clients log in UTC. This simplifies correlating actions between different services.
If you have any concerns or feedback on the above let me know.

### Exception handling

Finally, please don't forget to add a DefaultUncaughtExceptionHandler that logs the exception and kills the server.
