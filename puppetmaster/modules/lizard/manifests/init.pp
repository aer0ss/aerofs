class lizard {
    package{"aerofs-lizard":
        ensure => latest,
        notify => Service["lizard"],
        require => [
            Apt::Source["aerofs"]
        ],
    }

    file{"/opt/lizard/additional_config.py":
        ensure => present,
        source => "puppet:///modules/lizard/additional_config.py",
        require => Package["aerofs-lizard"],
        notify => Service["lizard"],
    }

    # Upstart job provided by deb, legacy init integration added here
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

    logrotate::log{"lizard":}
}
