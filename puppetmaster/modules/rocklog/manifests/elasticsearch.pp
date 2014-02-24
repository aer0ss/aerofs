class rocklog::elasticsearch {

    #
    # elasticsearch package and configuration
    #

    apt::source { "elasticsearch":
        location    => "http://packages.elasticsearch.org/elasticsearch/1.0/debian",
        release     => "stable",
        repos       => "main",
        include_src => false,
        key         => "Elasticsearch",
        key_source  => "http://packages.elasticsearch.org/GPG-KEY-elasticsearch"
    }

    package { "elasticsearch":
        ensure  => latest,
        require => Apt::Source["elasticsearch"]
    }

    service { "elasticsearch":
        ensure   => running,
        require  => Package["elasticsearch"]
    }

    file { "/etc/elasticsearch/elasticsearch.yml":
        ensure  => present,
        owner   => "elasticsearch",
        group   => "elasticsearch",
        mode    => "0644",
        source  => "puppet:///modules/rocklog/elasticsearch.yml",
        require => Package["elasticsearch"],
        notify  => Service["elasticsearch"]
    }

    file { "/etc/elasticsearch/mappings":
        ensure  => directory,
        owner   => "elasticsearch",
        group   => "elasticsearch",
        mode    => "0444",
        require => Package["elasticsearch"]
    }

    file { "/etc/elasticsearch/mappings/_default":
        ensure  => directory,
        owner   => "elasticsearch",
        group   => "elasticsearch",
        mode    => "0444",
        require => File["/etc/elasticsearch/mappings"]
    }

    file { "/etc/elasticsearch/mappings/_default/metrics.json":
        ensure  => present,
        owner   => "elasticsearch",
        group   => "elasticsearch",
        mode    => "0444",
        source  => "puppet:///modules/rocklog/metrics.json",
        require => File["/etc/elasticsearch/mappings/_default"],
        notify  => Service["elasticsearch"]
    }

    #
    # elasticsearch old-index cleaner
    #

    file { "/usr/bin/es_cleaner.py":
        source  => "puppet:///modules/rocklog/es_cleaner.py",
        owner   => "root",
        group   => "root",
        mode    => "0500"
    }

    cron { "daily_es_clean":
        command => "/usr/bin/es_cleaner.py -u http://localhost:9200 -d 18",
        user    => "root",
        hour    => 1,
        minute  => 0,
        require => File["/usr/bin/es_cleaner.py"]
    }

    #
    # nginx routes
    #
    # elasticsearch is reachable through both:
    # - triks.aerofs.com
    # - rocklog.aerofs.com:9200

    file { "/etc/nginx/sites-available/triks":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0644",
        content => template('rocklog/triks.erb'),
        require => [$nginxall],
        notify  => Service["nginx"]
    }

    file { "/etc/nginx/sites-enabled/triks":
        ensure  => link,
        target  => "/etc/nginx/sites-available/triks",
        notify  => Service["nginx"]
    }
}
