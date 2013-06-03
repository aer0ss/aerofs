class zephyr {
    package{"aerofs-zephyr":
        ensure => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/zephyr":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-zephyr"],
    }
    
    service { "zephyr":
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/zephyr"],
    }
}
