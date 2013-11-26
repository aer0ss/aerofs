class servlet::sp {
    package{"aerofs-sp":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::log{"sp":
        config_filename => "/usr/share/aerofs-sp/sp/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-sp"],
    }
}
