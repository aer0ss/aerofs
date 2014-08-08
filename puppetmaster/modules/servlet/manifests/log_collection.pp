class servlet::log_collection {
    package{"aerofs-log-collection":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ],
        notify => Service["tomcat6"],
    }

    servlet::log{"log-collection":
        config_filename => "/usr/share/aerofs-log-collection/log-collection/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-log-collection"],
    }
}
