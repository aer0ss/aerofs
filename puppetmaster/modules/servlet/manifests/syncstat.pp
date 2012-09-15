class servlet::syncstat(
    $mysql_sp_password,
    $mysql_syncstat_password,
    $mysql_endpoint,
    $verkehr_host,
    $cacert_location
) {
    include servlet

    package{"aerofs-syncstat":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    servlet::config{"/usr/share/aerofs-syncstat/syncstat/WEB-INF/web.xml":
        content => template("servlet/syncstat.web.xml.erb"),
        require => Package["aerofs-syncstat"]
    }

    $config_filename = "/usr/share/aerofs-syncstat/syncstat/WEB-INF/classes/log4j.properties"
    servlet::log{"/var/log/aerofs/syncstat.log":
        config_filename => $config_filename,
        require => Package["aerofs-syncstat"],
    }
}
