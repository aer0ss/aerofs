class lizard::nginx {
    # FIXME dep lost. Comment out for now.
    #include nginx-package
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
    file {"/etc/nginx/sites-available/lizard-admin":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0644",
        source => "puppet:///modules/lizard/lizard-admin",
        notify => Service["nginx"],
    }
    file {"/etc/nginx/sites-enabled/lizard-admin":
        ensure  => link,
        target  => "/etc/nginx/sites-available/lizard-admin",
        notify  => Service["nginx"],
    }
    file {"/etc/nginx/certs/browser.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///modules/lizard/aerofs_ssl/ssl.key",
        # FIXME dep lost. Comment out for now.
        #require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }
    file {"/etc/nginx/certs/browser.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "puppet:///modules/lizard/aerofs_ssl/ssl.cert",
        # FIXME dep lost. Comment out for now.
        #require => File["/etc/nginx/certs"],
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
