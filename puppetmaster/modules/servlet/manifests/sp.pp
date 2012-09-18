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

    class{"servlet::config::sp":
        mysql_password => $mysql_password,
        mysql_endpoint => $mysql_endpoint
    }
}
