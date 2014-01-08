class nginx-package {
    # note that nginx is just a meta package which depends on nginx-full or
    # nginx-light.  we want to make sure that we use the most up-to-date copy
    # of all three packages.
    package{"nginx":
        ensure => latest,
        require => [
            Apt::Source["aerofs"],
        ]
    }
    package{"nginx-common":
        ensure => latest,
        require => [
            Apt::Source["aerofs"],
        ]
    }
    package{"nginx-full":
        ensure => latest,
        require => [
            Apt::Source["aerofs"],
        ]
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
