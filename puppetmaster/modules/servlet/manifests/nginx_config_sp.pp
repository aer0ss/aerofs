#
# N.B. This class is only used by the public deployment.
#
class servlet::nginx_config_sp (
        $proxy_read_timeout = "60",
        $proxy_send_timeout = "60",
    ) {

    include servlet

    file {"/etc/nginx/certs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0400",
    }

    file {"/etc/nginx/certs/sp.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/sp.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"]
    }

    file {"/etc/nginx/certs/sp.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/sp.cert",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"]
    }

    file {"/etc/security/limits.d/nginx-max-files.conf":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0644",
        source => "puppet:///modules/servlet/nginx-max-files.conf",
        notify => Service["nginx"],
    }

    # N.B. we are using a custom nginx module so that we can configure the proxy read and send
    # timeouts.
    nginx::resource::vhost {"${fqdn}":
        listen_port          => '443',
        ssl                  => 'true',
        ssl_cert             => '/etc/nginx/certs/sp.cert',
        ssl_key              => '/etc/nginx/certs/sp.key',
        ssl_client_cert      => '/etc/ssl/certs/AeroFS_CA.pem',
        proxy                => 'http://127.0.0.1:8080',
        client_max_body_size => '4096m',
        proxy_read_timeout   => $proxy_read_timeout,
        proxy_send_timeout   => $proxy_send_timeout,
        ensure               => present,
        require              => File ["/etc/nginx/certs/sp.key", "/etc/nginx/certs/sp.cert"],
    }
}
