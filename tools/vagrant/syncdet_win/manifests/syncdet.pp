class syncdet {
    user {"aerofstest":
        ensure => present
    }

#    host { "persistent.syncfs.com":
#        ensure => present,
#        target => "c:/Windows/System32/drivers/etc/hosts",
#        ip => $::persistent_ip
#    }
}

include syncdet
