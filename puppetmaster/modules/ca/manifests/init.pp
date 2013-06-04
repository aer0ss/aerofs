class ca {
    package{[
        "aerofs-ca-server",
        "aerofs-ca-tools",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    package { [
        "screen"
        ]:
        ensure => latest,
    }
}
