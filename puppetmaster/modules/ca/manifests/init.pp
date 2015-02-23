class ca {
    common::service{"ca-server": }

    package{[
        "aerofs-ca-tools",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
