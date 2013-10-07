class ejabberd::firewall_rules(
    $port = 443,
    $ip_address = undef
) {
    firewall { "500 forward traffic for xmpp clients":
        table   => "nat",
        chain   => "PREROUTING",
        dport   => $port,
        action  => "redirect",
        toports => "5222",
        destination => $ip_address
    }
}
