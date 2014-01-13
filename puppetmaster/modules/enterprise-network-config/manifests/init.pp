class enterprise-network-config {
    file {"/usr/local/bin/enterprise-greeter":
        source => "puppet:///modules/enterprise-network-config/enterprise-greeter",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "755",
    }
    file {"/etc/network/interfaces":
        source => "puppet:///modules/enterprise-network-config/network-interfaces",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
    file {"/etc/network/interfaces.d":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "755",
    }
    file {"/etc/network/selected-network-type":
        source => "puppet:///modules/enterprise-network-config/selected-network-type",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
    file {"/etc/network/interfaces.d/dhcp":
        source => "puppet:///modules/enterprise-network-config/dhcp",
        require => File["/etc/network/interfaces.d"],
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
    # Upstream Ubuntu Enterprise Cloud images started splitting config out into
    # eth0.cfg.  When we add our networking magic, we get invalid confs, so
    # throw theirs out for now.
    file {"/etc/network/interfaces.d/eth0.cfg":
        ensure => absent,
    }
    file {"/usr/local/sbin/network-mapper":
        source => "puppet:///modules/enterprise-network-config/network-mapper",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "755",
    }
    file {"/etc/init/tty1.conf":
        source => "puppet:///modules/enterprise-network-config/tty1.conf",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
}
