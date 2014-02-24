class rocklog::retrace {
    #
    # required to build the retrace server and tornado
    #

    package { ["g++", "ant", "python-pip"]:
        ensure => latest
    }

    package{ "aerofs-rocklog":
        ensure => latest,
        require => Apt::Source["aerofs"]
    }

    #
    # requirements for the rocklog python app
    # FIXME (AG): this should probably be installed using a deb/requirements.txt/sdist
    #

    package { [
        "flask",
        "pyelasticsearch",
        "requests",
        "tornado",
        ]:
        provider => "pip",
        require  => Package["python-pip"]
    }

    file{ "/opt/rocklog/rocklog.cfg":
        content => template("rocklog/rocklog.cfg.erb"),
        require => Package["aerofs-rocklog"],
        notify  => Service["tornado"]
    }

    file{ "/etc/init/tornado.conf":
        source => "puppet:///modules/rocklog/tornado.conf",
        require => Package["aerofs-rocklog"],
        notify => Service["tornado"]
    }

    service{ "tornado":
        ensure => running,
        require => [File["/etc/init/tornado.conf"], Package["tornado"]],
        provider => "upstart"
    }

    exec{ "get retrace server":
        command => "/usr/bin/wget -qO- https://github.com/aerofs/RetraceServer/archive/v0.2.tar.gz | tar xz",
        cwd => "/opt/",
        creates => "/opt/RetraceServer-0.2",
        notify => Exec["build retrace server"]
    }

    file{ "/opt/retrace":
        ensure => link,
        target => "/opt/RetraceServer-0.2",
        require => Exec["get retrace server"]
    }

    exec{ "build retrace server":
        command => "/usr/bin/ant",
        cwd => "/opt/retrace",
        creates => "/opt/retrace/out/jar/RetraceServer.jar",
        require => [
            File["/opt/retrace"],
            Package["ant"]
        ],
        notify => Service["retrace_server"],
    }

    file { "/etc/init/retrace_server.conf":
        source => "puppet:///modules/rocklog/retrace_server.conf",
        mode => "755",
        require => Exec["build retrace server"],
        notify => Service["retrace_server"]
    }

    service { "retrace_server":
        ensure => running,
        provider => "upstart",
        require => File["/etc/init/retrace_server.conf"]
    }

    file { "/maps":
        ensure => directory,
        mode   => 2770,
        group  => "admin",
        owner  => "root",
    }

    #
    # nginx routes
    #

    file { "/etc/nginx/sites-available/rocklog":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        content => template('rocklog/rocklog.erb'),
        require => [$nginxall],
        notify  => Service["nginx"]
    }

    file { "/etc/nginx/sites-enabled/rocklog":
        ensure  => link,
        target  => "/etc/nginx/sites-available/rocklog",
        notify  => Service["nginx"]
    }
}
