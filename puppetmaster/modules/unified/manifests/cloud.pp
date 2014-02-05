class unified::cloud inherits unified
{
    file {"/etc/default/grub":
        source => "puppet:///modules/unified/grub-options-cloud",
    }
}
