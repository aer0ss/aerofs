class lizard (
    $mysql_password,
    $stripe_publishable_key,
    $stripe_secret_key,
    $hpc_aws_access_key,
    $hpc_aws_secret_key,
) {
    package{"aerofs-lizard":
        ensure => latest,
        notify => [ Service["lizard"], Service["lizard-internal"], Service["celery"]],
        require => [
            Apt::Source["aerofs"]
        ],
    }

    file{"/opt/lizard/additional_config.py":
        ensure => present,
        content => template("lizard/additional_config.py.erb"),
        require => Package["aerofs-lizard"],
        notify => [ Service["lizard"], Service["lizard-internal"]],
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

    # Celery task queue
    file{"/etc/init.d/celery":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-lizard"],
    }
    service{"celery":
        ensure => running,
        provider => upstart,
        enable => true,
        require => [
            File["/etc/init.d/celery"],
        ]
    }

    logrotate::log{"lizard":}
}
