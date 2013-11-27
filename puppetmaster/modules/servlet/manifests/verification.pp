class servlet::verification {
    package{"aerofs-verification":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::log{"verification":
        config_filename => "/usr/share/aerofs-verification/verification/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-verification"],
    }
}
