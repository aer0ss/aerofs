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

    # Disable until we decide what to do with jingle.
    #include stun

    # --------------
    # Charlie
    # --------------

    include charlie
    file{"/opt/charlie/config.py":
        source => "puppet:///modules/charlie/config.py",
        require => Package["aerofs-charlie"],
    }

    # --------------
    # Servlets
    # --------------

    include servlet::base

    include servlet::sp
    include servlet::verification
    include servlet::identity
    include servlet::log_collection

    # --------------
    # Auditor
    # --------------

    include auditor
    file{"/opt/auditor/resources":
        ensure => directory,
        require => Package["aerofs-auditor"]
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
    # XRay
    # --------------

    include xray

    # --------------
    # Zephyr
    # --------------

    include zephyr

    # --------------
    # Ejabberd
    # --------------

    class{"ejabberd":
        mysql_password => "password",
        port => 5222,
    }

    #---------------
    # REST Gateway
    #---------------

    include havre

    #---------------
    # RESTful SP
    #---------------

    include sparta

    #---------------
    # OAuth server
    #---------------

    include bifrost

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

    file {"/opt/sanity/probes/charlie.sh":
        source => "puppet:///modules/transient/probes/charlie.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/ejabberd.sh":
        source => "puppet:///modules/transient/probes/ejabberd.sh",
        require => Package["aerofs-sanity"],
    }

    # Disable until we decide what to do with jingle.
    #file {"/opt/sanity/probes/restund.sh":
    #    source => "puppet:///modules/transient/probes/restund.sh",
    #    require => Package["aerofs-sanity"],
    #}

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

    file {"/opt/sanity/probes/havre.sh":
        source => "puppet:///modules/transient/probes/havre.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/bifrost.sh":
        source => "puppet:///modules/transient/probes/bifrost.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/sparta.sh":
        source => "puppet:///modules/transient/probes/sparta.sh",
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
