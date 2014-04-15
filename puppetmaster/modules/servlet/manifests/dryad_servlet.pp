class servlet::dryad_servlet {
    package{"aerofs-dryad-servlet":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::log{"dryad-servlet":
        config_filename => "/usr/share/aerofs-dryad-servlet/dryad-servlet/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-dryad-servlet"],
    }
}
