class pd-aerofs {
    include private-common

    # --------------
    # STUN
    # --------------

    include stun

    # --------------
    # SP
    # --------------

    include servlet::base
    include servlet::sp
    include jeq

    # Do not include this file, let bootstrap generate it.
    file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        ensure => absent
    }

    # --------------
    # Verkehr
    # --------------

    include verkehr
    file{"/opt/verkehr/resources":
        ensure => directory,
        require => Package["aerofs-verkehr"]
    }

    # --------------
    # Zephyr
    # --------------

    include zephyr

    # --------------
    # Ejabberd
    # --------------

    class{"ejabberd":
        mysql_password => "temp123"
    }

    # --------------
    # Admin Panel
    # --------------

    # Nginx related config (relates to SP as well).
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

    # Custom webadmin things.
    define replace_line($file, $old_pattern, $new_pattern) {
        exec { "/bin/sed -i 's#$old_pattern#$new_pattern#g' $file":
            onlyif => "/bin/grep  '$old_pattern' '$file'",
        }
    }
    replace_line {"production.ini static assets":
        file => "/opt/web/production.ini",
        old_pattern => "static.prefix = .*",
        new_pattern => "static.prefix = static",
    }
    replace_line {"production.ini sp url":
        file => "/opt/web/production.ini",
        old_pattern => "sp.url = .*",
        new_pattern => "sp.url = https://localhost/sp",
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-aerofs/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }
}
