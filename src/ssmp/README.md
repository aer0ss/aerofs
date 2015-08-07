Java implementation of [SSMP](https://github.com/aerofs/ssmp), the Stupid-Simple
Messaging Protocol, which aims to be a lightweight alternative to XMPP, STOMP
and similar protocols.


License
-------

BSD 3-clause, see accompanying LICENSE file.


Dependencies
------------

  - JDK 1.8 or higher
  - [netty](http://netty.io) 3.10+
  - [slf4j](http://slf4j.org) 1.7+
  - [guava](https://github.com/google/guava) 17+


Protocol support
----------------

Supported:

  - anonymous LOGIN
  - client certificate authentication, w/ arbitrary path suffix
  - shared secret authentication


Unsupported:

  - open login

