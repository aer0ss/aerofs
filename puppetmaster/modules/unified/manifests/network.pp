class unified::network {
    # Include the iptables-persistent package and general firewall setup
    include firewall
    # This hack with preconfigure-iptables is needed because of the way we bake
    # our private deployment VMs.  In short: due to potentially mismatched kernel
    # versions in the bakery and chroot, we can't run modprobe iptables_* from the
    # chroot, so we have to trick the iptables-persistent package into not trying.
    file{"/tmp/preconfigure-iptables":
        ensure => present,
        owner => "root",
        group => "root",
        mode => "644",
        content => "iptables-persistent iptables-persistent/autosave_done boolean true",
    }

    exec{"import-iptables-debconf":
        require => File["/tmp/preconfigure-iptables"],
        command => "/usr/bin/debconf-set-selections /tmp/preconfigure-iptables",
    }

    Firewall {
        require => Exec["import-iptables-debconf"],
    }
    # INPUT chain:
    # allow RELATED,ESTABLISHED
    # allow 22    (ssh)
    # allow 80    (web)
    # allow 443   (web)
    # allow 3478 (and UDP!)
    # allow 4433  (sp service)
    # allow 5222  (ejabberd)
    # allow 5435  (config)
    # allow 8888  (zephyr)
    # allow 29438 (verkehr)
    # allow 8084  (havre tunnel)
    firewall {"000 allow RELATED, ESTABLISHED":
        state  => ['RELATED', 'ESTABLISHED'],
        proto  => 'all',
        action => 'accept',
    }->
    firewall { "001 accept all icmp":
        proto  => 'icmp',
        action => 'accept',
    }->
    firewall { '002 INPUT allow loopback':
        iniface => 'lo',
        chain   => 'INPUT',
        action  => 'accept',
    }->
    firewall {"100 allow ssh":
        table => "filter",
        chain => "INPUT",
        dport => 22,
        action => "accept",
    }->
    firewall {"200 allow http":
        table => "filter",
        chain => "INPUT",
        dport => 80,
        action => "accept",
    }->
    firewall {"201 allow https":
        table => "filter",
        chain => "INPUT",
        dport => 443,
        action => "accept",
    }->
    firewall {"202 allow stun":
        table => "filter",
        chain => "INPUT",
        dport => 3478,
        action => "accept",
    }->
    firewall {"203 allow https service port":
        table => "filter",
        chain => "INPUT",
        dport => 4433,
        action => "accept",
    }->
    firewall {"204 allow jabber":
        table => "filter",
        chain => "INPUT",
        dport => 5222,
        action => "accept",
    }->
    firewall {"205 allow zephyr":
        table => "filter",
        chain => "INPUT",
        dport => 8888,
        action => "accept",
    }->
    firewall {"206 allow verkehr subscribe":
        table => "filter",
        chain => "INPUT",
        dport => 29438 ,
        action => "accept",
    }->
    firewall {"207 allow havre tunnel":
        table => "filter",
        chain => "INPUT",
        dport => 8084 ,
        action => "accept",
    }->
    firewall {"208 allow bunker maintenance port":
        table => "filter",
        chain => "INPUT",
        dport => 4444,
        action => "accept",
    }->
    # deny all others
    firewall {"999 reject all":
        table => "filter",
        chain => "INPUT",
        action => "reject"
    }
}
