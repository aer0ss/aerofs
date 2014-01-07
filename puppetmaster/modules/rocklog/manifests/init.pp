class rocklog {
    package { [
        "g++",
        "ruby1.8",
        "rubygems",
        "ant"
        ]:
        ensure => latest,
        require => Exec["apt-get update"]
    }

    # requirements for the rocklog python app
    package { [
        "flask",
        "pyelasticsearch",
        "requests",
        "tornado",
        ]:
        provider => "pip"
    }

    # requirements for Kibana
    package{ [
        "unicorn",
        "bundler"
        ]:
        provider => gem,
        ensure => latest,
        require => Package["rubygems"]
    }

    exec{"get elasticsearch deb":
        command => "/usr/bin/wget http://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-0.20.4.deb",
        cwd => "/root/",
        creates => "/root/elasticsearch-0.20.4.deb",
        notify => Package["elasticsearch"]
    }

    package{ "elasticsearch" :
        provider => dpkg,
        ensure => latest,
        source => "/root/elasticsearch-0.20.4.deb",
        require => Exec["get elasticsearch deb"]
    }

    service{"elasticsearch":
        ensure => running,
        require => Package["elasticsearch"],
    }

    # Daily elasticsearch cleaner:
    file{ "/usr/bin/es_cleaner.py":
        source => "puppet:///modules/rocklog/es_cleaner.py",
        owner   => "root",
        group   => "root",
        mode    => "0500",
    }

    cron { "daily_es_clean":
        command => "/usr/bin/es_cleaner.py -u http://localhost:9200 -d 18",
        user    => "root",
        hour    => 1,
        minute  => 0,
        require => File["/usr/bin/es_cleaner.py"]
    }

    exec{"get kibana":
        command => "/usr/bin/wget -qO- https://github.com/rashidkpc/Kibana/archive/v0.2.0.tar.gz | tar xz",
        cwd => "/opt/",
        creates => "/opt/Kibana-0.2.0",
        notify => Exec["install kibana gems"]
    }

    file{ "/opt/kibana":
        ensure  => link,
        target  => "/opt/Kibana-0.2.0",
        require => Exec["get kibana"]
    }

    exec{"install kibana gems":
        command => "bundle install",
        cwd => "/opt/kibana",
        provider => "shell",
        notify => Service["unicorn"],
        require => [
            Package["bundler"],
            File["/opt/kibana"]
        ],
        refreshonly => true
    }

    file{ "/opt/kibana/KibanaConfig.rb":
        source => "puppet:///modules/rocklog/KibanaConfig.rb",
        require => Exec["get kibana"],
        notify => Service["unicorn"],
    }

    # patch Kibana for modern (secure) browsers that won't execute
    # javascript that is text/html. Bug is fixed at Kibana, but
    # isn't in the download archive for some reason.
    define replace_line($file, $old_pattern, $new_pattern) {
        exec { "/bin/sed -i 's#$old_pattern#$new_pattern#g' $file":
            onlyif => "/bin/grep  '$old_pattern' '$file'"
        }
    }

    replace_line {"patch the content-type for timezone.js":
        file => "/opt/kibana/kibana.rb",
        old_pattern => "erb :timezone$",
        new_pattern => "erb :timezone, :content_type => \"application/javascript\"",
        notify => Service["unicorn"]
    }

    file{ "/etc/init/unicorn.conf":
        source => "puppet:///modules/rocklog/unicorn.conf",
        require => [
            Package["unicorn"],
        ],
        notify => Service["unicorn"],
        mode => 755
    }

    service{"unicorn":
        ensure => "running",
        require => File["/etc/init/unicorn.conf"],
        provider => 'upstart',
    }

    package{"aerofs-rocklog":
        ensure => latest,
        require => Apt::Source["aerofs"]
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
        require => Package["tornado"],
        provider => "upstart"
    }

    include nginx

    file {"/etc/nginx/certs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0400",
    }

    file {"/etc/nginx/certs/ssl.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"]
    }

    file {"/etc/nginx/certs/ssl.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"],
        notify  => Service["nginx"]
    }

    nginx::resource::vhost {"${fqdn}-kibana":
        listen_port          => '443',
        ssl                  => 'true',
        ssl_cert             => '/etc/nginx/certs/ssl.cert',
        ssl_key              => '/etc/nginx/certs/ssl.key',
        ssl_client_cert      => '/etc/ssl/certs/AeroFS_CA.pem',
        proxy                => 'http://127.0.0.1:8080',
        client_max_body_size => '4096m',
        ensure               => present,
        require              => File ["/etc/nginx/certs/ssl.key", "/etc/nginx/certs/ssl.cert"],
    }

    nginx::resource::vhost {"${fqdn}-rocklog":
        listen_port          => '80',
        proxy                => 'http://127.0.0.1:8000',
        client_max_body_size => '4096m',
        ensure               => present,
    }

    file {"/etc/security/limits.d/nginx-max-files.conf":
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "0644",
        source => "puppet:///modules/rocklog/nginx-max-files.conf",
        notify => Service["nginx"],
    }

    exec{"get retrace server":
        command => "/usr/bin/wget -qO- https://github.com/aerofs/RetraceServer/archive/v0.2.tar.gz | tar xz",
        cwd => "/opt/",
        creates => "/opt/RetraceServer-0.2",
        notify => Exec["build retrace server"]
    }

    file{"/opt/retrace":
        ensure => link,
        target => "/opt/RetraceServer-0.2",
        require => Exec["get retrace server"]
    }

    exec{"build retrace server":
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

    service {"retrace_server":
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
}
