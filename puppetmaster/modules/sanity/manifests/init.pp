class sanity {
    package{"aerofs-sanity":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
