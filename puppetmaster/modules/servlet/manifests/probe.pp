class servlet::probe {
    package{"aerofs-probe":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ],
        notify => Service["tomcat6"],
    }

    servlet::log{"probe":
        config_filename => "/usr/share/aerofs-probe/probe/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-probe"],
    }
}
