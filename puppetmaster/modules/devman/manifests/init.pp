class devman {
    package { "aerofs-devman":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/devman":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-devman"],
    }

    service { "devman":
        tag => ['autostart-overridable'],
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/devman"],
    }
}
