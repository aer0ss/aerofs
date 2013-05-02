class stun {
    package{[
        "aerofs-stun",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
