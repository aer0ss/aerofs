class common::firewall {
    # Always persist firewall rules
    exec { 'persist-firewall':
        command     => '/sbin/iptables-save > /etc/iptables.rules',
        refreshonly => true,
    }

    # This was tricky to get working, but this is the Right Way^TM to do it.
    #
    # If /etc/iptables.rules does not exist, the network interface will not come up
    # and we will get locked out of a box.
    #
    # The notify line forces /etc/iptables.rules to exist, fixing our problem.
    line { 'iptables.restore':
        file    => "/etc/network/interfaces",
        ensure  => present,
        line    => "pre-up iptables-restore < /etc/iptables.rules",
        notify  => Exec["persist-firewall"],
    }
}
