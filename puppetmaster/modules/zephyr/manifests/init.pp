class zephyr {
    package{"aerofs-zephyr":
        ensure => installed,
        require => Apt::Source["aerofs"],
    }
}
