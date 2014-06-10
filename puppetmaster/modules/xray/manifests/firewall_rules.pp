class xray::firewall_rules(
    $port = 443,
    $ip_address = undef
) {
    firewall { "500 forward traffic to xray":
        table   => "nat",
        chain   => "PREROUTING",
        dport   => $port,
        jump    => "REDIRECT",
        toports => "9531",
        destination => $ip_address
    }
}
