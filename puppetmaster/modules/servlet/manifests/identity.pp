class servlet::identity {
    package{"aerofs-identity":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::log{"identity":
        config_filename => "/usr/share/aerofs-identity/identity/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-identity"],
    }
}
