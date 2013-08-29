class nginx-package {
    package{"nginx":
        ensure => present,
    }

    file {"/etc/nginx/sites-enabled/default":
        ensure => absent,
        require => Package["nginx"]
    }

    file{"/etc/nginx/certs":
        ensure => directory,
        require => Package["nginx"]
    }
}
