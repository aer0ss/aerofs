class pd-aerofs {
    include private-common
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

