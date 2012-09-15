class servlet::sp(
    $mysql_password,
    $mysql_endpoint
) {
    include servlet

    package{"aerofs-sp":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::config{"/usr/share/aerofs-sp/sp/WEB-INF/web.xml":
        content => template("servlet/sp.web.xml.erb"),
        require => Package["aerofs-sp"]
    }

    servlet::log{"/var/log/aerofs/sp.log":
        config_filename => "/usr/share/aerofs-sp/sp/WEB-INF/classes/log4j.properties",
        require         => Package["aerofs-sp"],
    }
}
