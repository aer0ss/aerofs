class static-config::nginx {
    include nginx-package

    service { "nginx":
        ensure     => running,
        enable     => true,
        hasstatus  => true,
        hasrestart => true,
    }
    file {"/etc/nginx/sites-available/aerofs-frontend":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0644",
        source => "puppet:///modules/static-config/aerofs-frontend",
        notify => Service["nginx"],
    }
    file {"/etc/nginx/sites-enabled/aerofs-frontend":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-frontend",
        require => [ Package["nginx-common"],
                     File["/etc/nginx/certs/config.cert"],
                     File["/etc/nginx/certs/config.key"],
                   ]
        notify  => Service["nginx"],
    }

    # SSL cert and key
    file {"/etc/nginx/certs/config.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/config.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }
    file {"/etc/nginx/certs/config.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "puppet:///aerofs_ssl/config.cert",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }

    # Ensure config backend exists on the box
    file {"/etc/nginx/backends-available":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0755",
    }
    file {"/etc/nginx/backends-available/aerofs-config":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0644",
        source => "puppet:///modules/static-config/aerofs-config",
        notify => Service["nginx"],
    }

    # Ensure config backend is enabled
    file {"/etc/nginx/backends-enabled":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0755",
    }
    file {"/etc/nginx/backends-enabled/aerofs-config":
        ensure => link,
        target => "/etc/nginx/backends-available/aerofs-config",
        notify => Service["nginx"],
    }
}
