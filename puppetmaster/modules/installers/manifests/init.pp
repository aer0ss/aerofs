class installers (
) {
    package { "aerofs-installers":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }
}
