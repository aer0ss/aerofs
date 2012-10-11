class zephyr::firewall_rules(
    $port = 443,
    $ip_address = undef
) {
    firewall { "500 forward traffic to zephyr":
        table   => "nat",
        chain   => "PREROUTING",
        dport   => $port,
        jump    => "REDIRECT",
        toports => "8888",
        destination => $ip_address
    }
}
