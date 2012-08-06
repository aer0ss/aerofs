#!/bin/bash
# This is an interactive script.
#
# This script will eventually be replaced by the web-based bootstrap app. This
# script is for developers ONLY and will be deleted soon.

SHARE=/mnt/share

# Set up the user storage so it is ready for the cert to be uploaded.
configure_user_storage()
{
    # Not the primary hard disk.
    disks=$(ls /dev/sd* | grep [0-9] | grep -v a)
    if [ "$disks" = "" ]
    then
        echo "No block devices have been detected. Using local storage."
        mkdir $SHARE
    else
        # Ask the user which device to use.
        echo "Found the following block devices:"

        counter=1
        for d in $disks
        do
            echo "  $counter. $d"
            counter=$[$counter+1]
        done
        end=$[$counter-1]

        echo -n "Use which device? (0 for local storage) "
        read number

        if [[ "$number" =~ ^[0-9]+$ ]]
        then
            if [ $number -gt $end ] || [ $number -lt 0 ]
            then
                echo "Error: number not in range."
                exit 1
            fi
        else
            echo "Error: not a number."
            exit 1
        fi

        # "Are you sure?"
        echo -n "Use device $number? [y/n] "
        read yesno

        if [ "$yesno" = "y" ] || [ "$yesno" = "yes" ]
        then
            echo "Using device $number for data storage."

            if [ $number -eq 0 ]
            then
                echo "Using local storage."
                mkdir $SHARE
            else
                # Set up the autofs entry.
                counter=1
                for d in $disks
                do
                    if [ $counter -eq $number ]
                    then
                        disk_final=$d
                        break
                    fi
                done

                # Create the autofs entries.
                fstype=$(parted -l $disk_final | grep primary | tail -1 | awk -F' ' '{print $6'})
                echo "/mnt /etc/auto.aerofs" > /etc/auto.master
                echo "share -fstype=$fstype :$disk_final" > /etc/auto.aerofs

                # Silently restart autofs to pull in the changes.
                echo "Restarting automounting service..."
                service autofs restart 1>/dev/null 2>/dev/null

                # Unfortunately we require a sleep here for the service to wake
                # up...
                sleep 5
            fi
        else
            echo "Error: please enter 'y' or 'n'."
            exit 1
        fi
    fi

    # Set up the share directories. If $SHARE doesn't exist by now, then there
    # has been an error.
    test -d $SHARE
    if [ $? -ne 0 ]
    then
        echo "Error: unable to setup data storage."
        exit 1
    fi

    # So the www can move the software package here. Individual kvm stores are
    # still private to root.
    chmod -R 777 $SHARE

    for img in $(ls ~root/images/*.img 2>/dev/null)
    do
        name=$(echo $(basename $img) | awk -F'.' '{print $1}')
        mkdir -p $SHARE/$name
    done

    mkdir -p $SHARE/.config
    chmod 777 $SHARE/.config
}

# Create the required hard links and config directories.
create_user_links()
{
    for dir in $(ls $SHARE)
    do
        mkdir -p $SHARE/$dir/.config
        echo $dir > $SHARE/$dir/.config/service.name

        for file in vmhost.addr cert.pem
        do
            dest=$SHARE/$dir/.config/$file
            test -f $dest
            if [ $? -ne 0 ]
            then
                ln $SHARE/.config/$file $dest
            fi
        done
    done
}

# Main script.
configure_user_storage

echo "Please upload your cert.pem file using your web browser."
echo -n "Press enter when you're done."

read dummy

test -f $SHARE/.config/cert.pem
if [ $? -ne 0 ]
then
    echo "Error: cert.pem no uploaded."
    exit 1
fi

echo -n "Where is the admin panel? "
read admin
echo $admin > $SHARE/.config/admin.addr

/usr/local/aerofs/tools/vmhost-id.sh
/usr/local/aerofs/tools/vmhost-addr.sh

create_user_links

echo
echo "Done. You're good to go."
