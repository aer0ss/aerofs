class dryad::nginx {
    include nginx-package
    $nginx_all = Package["nginx", "nginx-common", "nginx-full"]

    service { "nginx":
        tag         => ["autostart-overridable"],
        ensure      => running,
        enable      => true,
        hasstatus   => true,
        hasrestart  => true,
        require     => [
            $nginx_all,
            Package["aerofs-dryad"],
            File[
                "/etc/nginx/sites-enabled/aerofs-custom",
                "/etc/nginx/backends-enabled/aerofs-dryad"
            ],
        ],
    }

    file { "/etc/nginx/certs/custom.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/dryad.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }

    file { "/etc/nginx/certs/custom.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/dryad.cert",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }

    file { ["/etc/nginx/backends-available",
            "/etc/nginx/backends-enabled",
            "/etc/nginx/sites-available",
            "/etc/nginx/sites-enabled"]:
        ensure  => directory,
        owner   => "root",
        group   => "root",
        mode    => "0755",
        require => $nginx_all,
    }

    file { ["/etc/nginx/sites-available/default"]:
        ensure  => absent,
        require => $nginx_all,
    }

    file { "/etc/nginx/sites-available/aerofs-custom":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "/opt/dryad/aerofs-dryad-nginx-site",
        require => [
            Package["aerofs-dryad"],
            File[
                "/etc/nginx/sites-available",
                "/etc/nginx/certs/custom.key",
                "/etc/nginx/certs/custom.cert"
            ],
        ],
        notify  => Service["nginx"],
    }

    file { "/etc/nginx/sites-enabled/aerofs-custom":
        ensure  => link,
        owner   => "root",
        group   => "root",
        mode    => "0755",
        target  => "/etc/nginx/sites-available/aerofs-custom",
        require => File["/etc/nginx/sites-available/aerofs-custom"],
        notify  => Service["nginx"],
    }

    file { "/etc/nginx/backends-available/aerofs-dryad":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "/opt/dryad/aerofs-dryad-nginx-backend",
        require => [
            Package["aerofs-dryad"],
            File["/etc/nginx/backends-available"],
        ],
        notify  => Service["nginx"],
    }

    file { "/etc/nginx/backends-enabled/aerofs-dryad":
        ensure  => link,
        owner   => "root",
        group   => "root",
        mode    => "0755",
        target  => "/etc/nginx/backends-available/aerofs-dryad",
        require => File["/etc/nginx/backends-available/aerofs-dryad"],
        notify  => Service["nginx"],
    }
}
