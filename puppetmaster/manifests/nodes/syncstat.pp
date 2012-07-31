node /^syncstat\.aerofs\.com$/ inherits default {

    include syncstat

    user{"tomcat6":
        shell => "/bin/false",
        home => "/usr/share/tomcat6",
    }

    mkfs::create{ "ext4 /dev/xvdf":
        type      => "ext4",
        partition => "/dev/xvdf",
    }

    file { "/data":
        ensure => directory,
        owner  => root,
        group  => root,
    }
    mount { "/data":
        atboot  => true,
        device  => "/dev/xvdf",
        ensure  => mounted,
        fstype  => "ext4",
        options => "defaults",
        dump    => "0",
        pass    => "0",
        require => [ Mkfs::Create["ext4 /dev/xvdf"], File["/data"]],
    }

    syncstat::mount { "mount-syncstat-data": }

    class { "tomcat6": #install tomcat6 and tomcat6-admin
        require => [ SyncStat::Mount["mount-syncstat-data"], ],
    }

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

    nginx::resource::vhost { "${fqdn}":
        listen_port          => '443',
        ssl                  => 'true',
        ssl_cert             => '/etc/nginx/certs/ssl.cert',
        ssl_key              => '/etc/nginx/certs/ssl.key',
        proxy                => 'http://127.0.0.1:8080',
        client_max_body_size => '100m',
        ensure               => present,
        require              => File ["/etc/nginx/certs/ssl.key", "/etc/nginx/certs/ssl.cert"],
    }

    $tomcat6_user = hiera("tomcat6_manager")

    tomcat6::add_user{"manager":
        password => $tomcat6_user[password],
        roles    => $tomcat6_user[roles],
        id       => "001",
    }

    file {"/etc/tomcat6/policy.d/50local.policy":
        require => Package["tomcat6"],
        owner   => "root",
        group   => "tomcat6",
        mode    => "644",
        source  => "puppet:///syncstat/50local.policy",
        notify  => Class["tomcat6::service"],
    }

    users::add_user {
        [ hiera('dev_users') ]:
    }
}
