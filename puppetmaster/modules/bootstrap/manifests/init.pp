class bootstrap (
) {
    package { "aerofs-bootstrap":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }
}
