class bootstrap (
) {
    package { "aerofs-bootstrap":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    logrotate::log{"bootstrap": }
}
