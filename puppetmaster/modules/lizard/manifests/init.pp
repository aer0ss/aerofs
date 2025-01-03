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

    file{"/opt/lizard/docker-ca.pem":
        source  => "puppet:///aerofs_docker/docker-ca.pem",
        ensure  => present,
        require => Package["aerofs-lizard"],
    }
    file{"/opt/lizard/docker-client-cert.pem":
        source  => "puppet:///aerofs_docker/docker-client-cert.pem",
        ensure  => present,
        require => Package["aerofs-lizard"],
    }
    file{"/opt/lizard/docker-client-key.pem":
        source  => "puppet:///aerofs_docker/docker-client-key.pem",
        ensure  => present,
        require => Package["aerofs-lizard"],
    }

    file{"/var/www/.ssh":
        ensure  => 'directory',
        owner   => 'www-data',
        require => Package["aerofs-lizard"],
    }
    file{"/opt/lizard/hpc-server-config":
        ensure  => 'directory',
        owner   => 'www-data',
        require => Package["aerofs-lizard"],
    }
    file{"/opt/lizard/hpc-server-config/secrets":
        ensure  => 'directory',
        owner   => 'www-data',
        require => File["/opt/lizard/hpc-server-config"],
    }
    file{"/opt/lizard/hpc-server-config/secrets/hpc-key.pem":
        source  => "puppet:///aerofs_hpc/hpc-key.pem",
        ensure  => present,
        owner   => 'www-data',
        mode    => 0400,
        require => File["/opt/lizard/hpc-server-config/secrets"],
    }
    file{"/opt/lizard/hpc-server-config/secrets/aerofs.com.key":
        source  => "puppet:///aerofs_hpc/aerofs.com.key",
        ensure  => present,
        owner   => 'www-data',
        require => File["/opt/lizard/hpc-server-config/secrets"],
    }
    file{"/opt/lizard/hpc-server-config/secrets/aerofs.com.crt":
        source  => "puppet:///aerofs_hpc/aerofs.com.crt",
        ensure  => present,
        owner   => 'www-data',
        require => File["/opt/lizard/hpc-server-config/secrets"],
    }
}
