class servlet::sv {
    package{"aerofs-sv":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ],
        notify => Service["tomcat6"],
    }

    file {"/var/svlogs_prod":
        ensure => directory,
        mode   => "0777"
    }

    file {"/var/svlogs_prod/defect":
        ensure  => directory,
        require => File["/var/svlogs_prod"],
        mode    => "0777"
    }
}
