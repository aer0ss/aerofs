# == Class: servlet
#
# Servlet contains all the functionality to run tomcat6 servlets. Each individual application
# has it's own manifest. (ex. servlet::syncstat)
#
# === Parameters
#
#
# === Variables
#
#
# === Examples
#
#  include servlet
#  class {"servlet::syncstat":
#    mysql_sp_username => "foo",
#    ...
#  }
#
# === Authors
#
# Peter Hamilton<peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc
#
class servlet {

    include tomcat6

    $tomcat6_user = hiera("tomcat6_manager")
    tomcat6::user {"manager":
        password => $tomcat6_user[password],
        roles    => $tomcat6_user[roles],
        id       => "001",
    }

    include nginx

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
        require => File["/etc/nginx/certs"],
    }

    file {"/etc/nginx/certs/ssl.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"],
    }

    nginx::resource::vhost {"${fqdn}":
        listen_port          => '443',
        ssl                  => 'true',
        ssl_cert             => '/etc/nginx/certs/ssl.cert',
        ssl_key              => '/etc/nginx/certs/ssl.key',
        proxy                => 'http://127.0.0.1:8080',
        client_max_body_size => '100m',
        ensure               => present,
        require              => File ["/etc/nginx/certs/ssl.key", "/etc/nginx/certs/ssl.cert"],
    }
}
