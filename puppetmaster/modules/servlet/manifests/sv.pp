class servlet::sv(
    $mysql_password,
    $mysql_endpoint
) {
    include servlet

    package{"aerofs-sv":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    package{"proguard":
        ensure => installed,
    }

    servlet::config{"/usr/share/aerofs-sv/sv/WEB-INF/web.xml":
        content => template("servlet/sv.web.xml.erb"),
        require => Package["aerofs-sv"]
    }

    servlet::log{"/var/log/aerofs/sv.log":
        config_filename => "/usr/share/aerofs-sv/sv/WEB-INF/classes/log4j.properties",
        require         => Package["aerofs-sv"]
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

    file {"/maps":
        ensure  => directory,
        mode    => "0777"
    }
}
