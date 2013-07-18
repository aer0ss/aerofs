class pd-app-transient {
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
        mysql_password => "password",
        port => 8139,
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
    file {"/etc/nginx/sites-available/aerofs-web-sp":
        source => "puppet:///modules/pd-app-transient/aerofs-web-sp",
        require => Package["nginx"],
    }
    file{ "/etc/nginx/sites-enabled/aerofs-web-sp":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-web-sp",
        require => File["/etc/nginx/sites-available/aerofs-web-sp"],
    }
    file {"/etc/nginx/sites-enabled/aerofs-web":
        ensure => absent,
        require => Package["aerofs-web"]
    }

    # Custom webadmin things.
    define replace_line($file, $old_pattern, $new_pattern) {
        exec { "/bin/sed -i 's#$old_pattern#$new_pattern#g' $file":
            onlyif => "/bin/grep  '$old_pattern' '$file'",
            require => Package["aerofs-web"],
        }
    }
    replace_line {"production.ini static prefix":
        file => "/opt/web/production.ini",
        old_pattern => "static.prefix = .*",
        new_pattern => "static.prefix = static",
    }
    replace_line {"production.ini installer prefix":
        file => "/opt/web/production.ini",
        old_pattern => "installer.prefix = .*",
        new_pattern => "installer.prefix = static",
    }
    replace_line {"production.ini sp url":
        file => "/opt/web/production.ini",
        old_pattern => "sp.url = .*",
        new_pattern => "sp.url = https://localhost/sp",
    }
    replace_line {"production.ini deployment mode":
        file => "/opt/web/production.ini",
        old_pattern => "deployment.mode = .*",
        new_pattern => "deployment.mode = private",
    }
    file{ "/opt/web/web/static/installers":
        ensure  => link,
        target  => "/opt/repackaging/installers/modified",
        require => Package["aerofs-repackaging"],
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-app-transient/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/ejabberd.sh":
        source => "puppet:///modules/pd-app-transient/probes/ejabberd.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/tomcat6.sh":
        source => "puppet:///modules/pd-app-transient/probes/tomcat6.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/verkehr.sh":
        source => "puppet:///modules/pd-app-transient/probes/verkehr.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/zephyr.sh":
        source => "puppet:///modules/pd-app-transient/probes/zephyr.sh",
        require => Package["aerofs-sanity"],
    }

    # --------------
    # Repackaging
    # --------------

    include repackaging
}
