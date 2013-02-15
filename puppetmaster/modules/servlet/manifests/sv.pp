class servlet::sv(
    $mysql_password,
    $mysql_endpoint
) {
    include servlet

    class{"servlet::config::sv":
        mysql_password => $mysql_password,
        mysql_endpoint => $mysql_endpoint
    }

    package{"aerofs-sv":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
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
