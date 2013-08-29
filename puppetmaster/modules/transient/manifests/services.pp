#
# The services class include all services and configuration on the box EXCEPT
# for nginx, bootstrap and admin panel configuration (since these services need
# to be configured differently depending on the deployment mode.
#
class transient::services {
    include private-common

    # --------------
    # Nginx
    # --------------

    include nginx-package

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

    class{"web":
        stripe_publishable_key => "gibberish",
        stripe_secret_key => "gibberish",
        uwsgi_port => 8081,
    }

    file{ "/opt/web/web/static/installers":
        ensure  => link,
        target  => "/opt/repackaging/installers/modified",
        require => Package["aerofs-repackaging"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/ejabberd.sh":
        source => "puppet:///modules/transient/probes/ejabberd.sh",
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
