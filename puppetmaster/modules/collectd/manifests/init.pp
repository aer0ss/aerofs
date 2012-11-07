class collectd(
    $prefix
) {
    package{"collectd":
        ensure => latest
    }

    service{"collectd":
        ensure => running
    }

    $plugins = [
        "cpu",
        "df",
        "disk",
        "interface",
        "irq",
        "load",
        "memory",
        "network",
        "processes",
        "users",
    ]

    file{"/etc/collectd/collectd.conf":
        content => template("collectd/collectd.conf.erb"),
        notify => Service["collectd"]
    }
}
