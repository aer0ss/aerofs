class db-backup {
    package{[
        "aerofs-db-backup",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
