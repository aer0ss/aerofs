class lizard {
    package{"aerofs-lizard":
        ensure => latest,
        notify => [ Service["lizard"], Service["lizard-internal"], ],
        require => [
            Apt::Source["aerofs"]
        ],
    }

    file{"/opt/lizard/additional_config.py":
        ensure => present,
        source => "puppet:///modules/lizard/additional_config.py",
        require => Package["aerofs-lizard"],
        notify => [ Service["lizard"], Service["lizard-internal"], ],
    }

    # Upstart job provided by deb, legacy init integration added here
    # Externally-visible port.
    file{"/etc/init.d/lizard":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-lizard"],
    }
    service{"lizard":
        ensure => running,
        provider => upstart,
        enable => true,
        require => [
            File["/etc/init.d/lizard"],
        ]
    }

    # Internally-visible port (admin site)
    file{"/etc/init.d/lizard-internal":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-lizard"],
    }
    service{"lizard-internal":
        ensure => running,
        provider => upstart,
        enable => true,
        require => [
            File["/etc/init.d/lizard"],
        ]
    }

    logrotate::log{"lizard":}
}
