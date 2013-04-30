class webadmin::nginx {
    file {"/etc/nginx/certs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0400",
    }
     file {"/etc/nginx/certs/ssl.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
        require => File["/etc/nginx/certs"]
    }

    file {"/etc/nginx/certs/ssl.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"]
    }
}
