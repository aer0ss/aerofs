class transient {
    include private-common

    # --------------
    # STUN
    # --------------

    include stun

    # --------------
    # Devman
    # --------------

    include devman

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
    class{"web":
        stripe_publishable_key => "gibberish",
        stripe_secret_key => "gibberish",
        uwsgi_port => 8081,
    }
    file {"/etc/nginx/sites-available/aerofs-transient":
        source => "puppet:///modules/transient/aerofs-transient",
        require => Package["nginx"],
    }
    file{ "/etc/nginx/sites-enabled/aerofs-transient":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-transient",
        require => File["/etc/nginx/sites-available/aerofs-transient"],
    }
    file {"/etc/nginx/sites-enabled/default":
        ensure => absent,
        require => Package["nginx"]
    }

    # Custom web things.
    file{ "/opt/web/web/static/installers":
        ensure  => link,
        target  => "/opt/repackaging/installers/modified",
        require => Package["aerofs-repackaging"],
    }
    file {"/opt/web/production.ini":
        source => "puppet:///modules/transient/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/transient/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/ejabberd.sh":
        source => "puppet:///modules/transient/probes/ejabberd.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/nginx.sh":
        source => "puppet:///modules/transient/probes/nginx.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/restund.sh":
        source => "puppet:///modules/transient/probes/restund.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/tomcat6.sh":
        source => "puppet:///modules/transient/probes/tomcat6.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/uwsgi.sh":
        source => "puppet:///modules/transient/probes/uwsgi.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/verkehr.sh":
        source => "puppet:///modules/transient/probes/verkehr.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/zephyr.sh":
        source => "puppet:///modules/transient/probes/zephyr.sh",
        require => Package["aerofs-sanity"],
    }

    # --------------
    # Repackaging
    # --------------

    include repackaging

    # --------------
    # Disable auto-start
    # --------------

    Service <| tag == 'autostart-overridable' |> {
        ensure => stopped,
    }
}
