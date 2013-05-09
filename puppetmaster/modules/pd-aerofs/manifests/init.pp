class pd-aerofs {
    include private-common
    include servlet::base
    include servlet::sp
    include devman
    include jeq

    # Do not include this file, let bootstrap generate it.
    file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        ensure => absent
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

    # Nginx related config.
    package{"nginx":
        ensure => present,
    }
    file{"/etc/nginx/certs":
        ensure => directory,
        require => Package["nginx"]
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

    # Bootstrap things.
    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-aerofs/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }
}
