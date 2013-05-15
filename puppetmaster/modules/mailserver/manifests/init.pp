class mailserver {
    $postfix_smtp_listen = "all"
    include postfix

    $postfix_tls_key = "/etc/postfix/ssl.key"
    file { $postfix_tls_key :
        ensure  => present,
        owner   => "postfix",
        group   => "postfix",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
    }
    $postfix_tls_cert = "/etc/postfix/ssl.cert"
    file { $postfix_tls_cert :
        ensure  => present,
        owner   => "postfix",
        group   => "postfix",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
    }

    postfix::config {
        "smtpd_use_tls":              value => "yes";
        "smtpd_tls_key_file":         value => $postfix_tls_key;
        "smtpd_tls_cert_file":        value => $postfix_tls_cert;
        "smtpd_tls_loglevel":        value  => "3";
        "smtpd_tls_received_header": value  => "yes";
        # We allow mail from anywhere in the production network of the VPC,
        # since SP sends email through SV and such.
        # We do not send emails on behalf of CI.
        "mynetworks":                value  => "172.16.0.0/24"
    }
}
