class lizard::nginx {
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
        source => "puppet:///modules/lizard/aerofs-frontend",
        notify => Service["nginx"],
    }
    file {"/etc/nginx/sites-enabled/aerofs-frontend":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-frontend",
        notify  => Service["nginx"],
    }
    file {"/etc/nginx/certs/browser.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }
    file {"/etc/nginx/certs/browser.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }

    file{"/etc/nginx/backends-enabled":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0755",
    }
    file{"/etc/nginx/backends-enabled/lizard":
        ensure => link,
        target => '/etc/nginx/backends-available/lizard',
        notify => Service["nginx"],
    }
}
