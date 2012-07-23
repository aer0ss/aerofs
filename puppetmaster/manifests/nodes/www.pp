node /^www\d*\.aerofs\.com$/ inherits default {

    class { "nginx": }

    file { "/etc/nginx/certs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0400",
    }

    file { "/etc/nginx/certs/ssl.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
        require => File["/etc/nginx/certs"],
    }

    file { "/etc/nginx/certs/ssl.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"],
    }

    file { "/var/www":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0755",
    }
    
    nginx::resource::vhost { "${fqdn}-web":
        ensure      => present,
        listen_port => "80",
        name        => "${fqdn}",
        www_dir     => "/var/www",
        require     => File[ "/var/www" ],
    }

    nginx::resource::vhost { "${fqdn}-ssl":
        ensure      => present,
        listen_port => "443",
        name        => "${fqdn}",
        ssl         => 'true',
        ssl_cert    => '/etc/nginx/certs/ssl.cert',
        ssl_key     => '/etc/nginx/certs/ssl.key',
        www_dir     => "/var/www",
        require     => File ["/etc/nginx/certs/ssl.key", "/etc/nginx/certs/ssl.cert", "/var/www"],
    }

    users::add_user {
        [ hiera('dev_users') ]:
    }

    #TODO: need to automaticaly populate the webserver
}
