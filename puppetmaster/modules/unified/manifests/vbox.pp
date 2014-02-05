class unified::vbox inherits unified
{
    file {"/etc/default/grub":
        source => "puppet:///modules/unified/grub-options-vbox",
    }
}
