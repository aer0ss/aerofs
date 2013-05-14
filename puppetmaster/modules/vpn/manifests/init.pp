class vpn {
    #TODO (PH) Get the rest of the vpn stuff in this script
    file {"/root/make_vpn_client":
        source => "puppet:///modules/vpn/make_vpn_client",
        mode => "0775"
    }
}
