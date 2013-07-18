class repackaging (
) {
    package { "aerofs-repackaging":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }
}
