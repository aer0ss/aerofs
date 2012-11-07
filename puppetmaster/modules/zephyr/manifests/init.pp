class zephyr {
    package{"aerofs-zephyr":
        ensure => latest,
        require => Apt::Source["aerofs"],
    }
}
