class rocklog::kibana {
    exec{ "get kibana":
        command => "/usr/bin/wget -qO- https://download.elasticsearch.org/kibana/kibana/kibana-3.0.0milestone5.tar.gz | tar xz",
        cwd => "/opt/",
        creates => "/opt/kibana-3.0.0milestone5"
    }

    file{ "/opt/kibana":
        ensure  => link,
        target  => "/opt/kibana-3.0.0milestone5",
        require => Exec["get kibana"]
    }

    file{ "/opt/kibana/config.js":
        content => template("rocklog/config.js.erb"),
        require => Exec["get kibana"],
        notify  => Service["nginx"],
    }

    #
    # nginx routes
    #

    file { "/etc/nginx/sites-available/kibana":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        require => [$nginxall, File["/etc/nginx/certs/browser.key", "/etc/nginx/certs/browser.cert"]],
        content => template('rocklog/kibana.erb'),
        notify  => Service["nginx"]
    }

    file { "/etc/nginx/sites-enabled/kibana":
        ensure  => link,
        target  => "/etc/nginx/sites-available/kibana",
        notify  => Service["nginx"]
    }
}
