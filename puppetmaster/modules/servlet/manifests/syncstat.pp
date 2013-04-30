class servlet::syncstat {
    package{"aerofs-syncstat":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    package{"aerofs-syncstat-tools":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    # Java heap space for tomcat.
    line{ "tomcat java heap space":
         ensure => present,
         file => "/etc/default/tomcat",
         line => "JAVA_OPTS=\"-Djava.awt.headless=true -Xmx512m -XX:+UseConcMarkSweepGC\"",
         require => Package["aerofs-syncstat"]
    }
}
