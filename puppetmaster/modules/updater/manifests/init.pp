class updater (
) {
    package { "aerofs-updater":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }
}
