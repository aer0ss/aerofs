class lizard {
    package{"aerofs-lizard":
        ensure => latest,
        notify => Service["lizard"],
        require => [
            Apt::Source["aerofs"]
        ],
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
