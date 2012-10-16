class servlet::syncstat(
    $mysql_sp_password,
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

    class{"servlet::config::syncstat":
        mysql_sp_password       => $mysql_sp_password,
        mysql_endpoint          => $mysql_endpoint,
        verkehr_host            => $verkehr_host,
        cacert_location         => $cacert_location
    }
}
