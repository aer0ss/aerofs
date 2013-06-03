class pd-database {
    include private-common

    # --------------
    # MySQL
    # --------------

    # MySQL client and MySQL server.
    include mysql
    include mysql::server

    # Should get pulled via apt dependency, but add it here just for good
    # measure.
    package {"aerofs-spdb":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # --------------
    # Redis
    # --------------

    # Redis in AOF (append only file) mode.
    include redis::aof

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-database/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/database.sh":
        source => "puppet:///modules/pd-database/database.sh",
        require => Package["aerofs-sanity"],
    }
}

