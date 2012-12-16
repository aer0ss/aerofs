class servlet::tools {
    package{"aerofs-servlet-tools":
        ensure => latest,
        require => Apt::Source["aerofs"],
    }
}
