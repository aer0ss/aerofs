class verkehr::firewall_rules(
    $subscribe_port = 443
) {
    firewall { "500 forward traffic for verkehr subscribers on port 443":
        table   => "nat",
        chain   => "PREROUTING",
        iniface => "eth0",
        dport   => $subscribe_port,
        jump    => "REDIRECT",
        toports => "29438"
    }
}
