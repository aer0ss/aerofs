class common (
    $aptkey,
    $repo
) {
    include motd
    include puppet
    include common::logs
    include common::firewall

    Exec {
        path => [
            '/usr/local/bin',
            '/opt/local/bin',
            '/usr/bin',
            '/usr/sbin',
            '/bin',
            '/sbin',],
        logoutput => true,
    }

    package { [
        "default-jdk",
        "htop",
        "dstat",
        "ntp",
        "iftop"
        ]:
        ensure => latest,
    }

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/${repo}",
        repos       => "main",
        include_src => false,
        key         => "${aptkey}",
        key_server  => "pgp.mit.edu",
    }
}
