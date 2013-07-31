class devman {
    package { "aerofs-devman":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    service { "devman":
        tag => ['autostart-overridable'],
        ensure => running,
        provider => upstart,
        require => Package["aerofs-devman"],
    }
}
