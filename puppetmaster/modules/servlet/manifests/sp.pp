class servlet::sp {
    package{"aerofs-sp":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
