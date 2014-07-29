class common (
    $aptkey,
    $repo
) {
    # N.B. dstat and *top's are included as dev tools only.
    package { [
        "default-jdk",
        "vim",
        "ntp",
        "unzip",
        "zip",
        "htop",
        "dstat",
        "iftop",
        "iperf"
        ]:
        ensure => latest,
    }

    include common::logs

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/${repo}",
        repos       => "main",
        include_src => false,
        key         => "${aptkey}",
        key_server  => "pgp.mit.edu",
    }
}
