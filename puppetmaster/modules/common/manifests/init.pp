class common (
    $aptkey,
    $repo
) {
    package { [
        "default-jdk",
        "htop",
        "dstat",
        "ntp",
        "iftop"
        ]:
        ensure => latest,
    }

    include motd
    include common::logs
    include common::firewall

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/${repo}",
        repos       => "main",
        include_src => false,
        key         => "${aptkey}",
        key_server  => "pgp.mit.edu",
    }
}
