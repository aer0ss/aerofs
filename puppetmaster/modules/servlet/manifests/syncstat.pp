class servlet::syncstat {
    package{"aerofs-syncstat":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::log{"syncstat":
        config_filename => "/usr/share/aerofs-syncstat/syncstat/WEB-INF/classes/logback.xml",
        log_level       => "WARN",
        require         => Package["aerofs-syncstat"],
    }

    # Java heap space for tomcat.
    line{ "tomcat java heap space":
         ensure => present,
         file => "/etc/default/tomcat",
         line => "JAVA_OPTS=\"-Djava.awt.headless=true -Xmx512m -XX:+UseConcMarkSweepGC\"",
         require => Package["aerofs-syncstat"]
    }
}
