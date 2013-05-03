class pd-aerofs {
    include common::logs
    include common::firewall
    Exec {
        path => [
            '/usr/local/bin',
            '/opt/local/bin',
            '/usr/bin',
            '/usr/sbin',
            '/bin',
            '/sbin',],
        logoutput => true,
    }
    package { [
        "default-jdk",
        "htop",
        "dstat",
        "ntp",
        "iftop"
        ]:
        ensure => latest,
    }
    include servlet::base
    include servlet::sp
    include devman
    include jeq

    file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        ensure => absent,
        require => Package["aerofs-sp"]
    }

    include verkehr
    file{"/opt/verkehr/resources":
        ensure => directory,
        require => Package["aerofs-verkehr"]
    }
    include zephyr

    class{"ejabberd":
        mysql_password => "temp123"
    }

    package{"nginx":
        ensure => present,
    }

    file{"/etc/nginx/certs":
        ensure => directory,
        require => Package["nginx"]
    }

    $key = '64E72541'
    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/production",
        repos       => "main",
        include_src => false,
        key         => $key,
        key_server  => "pgp.mit.edu",
    }

    package { "aerofs-bootstrap":
        ensure => installed,
        require => Apt::Source["aerofs"],
    }

    class{"webadmin":
        stripe_publishable_key => "gibberish",
        stripe_secret_key => "gibberish",
        uwsgi_port => 8081,
    }

    file {"/etc/nginx/conf.d/vhosts.conf":
        source => "puppet:///modules/pd-aerofs/vhosts.conf",
        require => Package["nginx"],
    }

    file {"/etc/nginx/sites-enabled/aerofsconfig":
        ensure => absent,
        require => Package["aerofs-web"]
    }
}

