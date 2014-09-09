# AeroFS Defects and Metrics Node
class rocklog(
    $triksdn
) {
    include nginx-package

    $nginxall = [Package["nginx"], Package["nginx-common"], Package["nginx-full"]]

    service { "nginx":
        ensure     => running,
        enable     => true,
        hasstatus  => true,
        hasrestart => true,
        require    => $nginxall
    }

    file { "/etc/security/limits.d/nginx-max-files.conf":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "puppet:///modules/rocklog/nginx-max-files.conf",
        require => $nginxall,
        notify  => Service["nginx"]
    }

    file {"/etc/nginx/certs/service.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/rocklog.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"],
    }

    file {"/etc/nginx/certs/service.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        source  => "puppet:///aerofs_ssl/rocklog.cert",
        require => File["/etc/nginx/certs"],
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

    logrotate::log{"elasticsearch":}

    include rocklog::elasticsearch
    include rocklog::kibana
    include rocklog::retrace
}
