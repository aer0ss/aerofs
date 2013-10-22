#!/bin/bash

FirstDefault=Firsty
First=$FirstDefault
LastDefault=Lasto
Last=$LastDefault
PasswdDefault=uiuiui
Passwd=$PasswdDefault

CurlOutput="-o /dev/null"
WebHostDefault=unified.syncfs.com
WebHost=$WebHostDefault
DbHostDefault=unified.syncfs.com
DbHost=$DbHostDefault
DbUser=vagrant
Userid=
Verbose=0

Usage()
{
    [ $# -gt 0 ] && echo -e "\n$@\n"
    echo "$0 [-v] [-w webhost ] [ -d dbhost ] [-f first] [-l last] -u userid -p password"
    echo ""
    echo "      -d <host>   Hostname of the box running the user database (default $DbHostDefault)"
    echo "      -w <host>   Hostname of the box running the website (default $WebHostDefault)"
    echo ""
    echo "      -u <userid> Username (email address) to create. No default."
    echo "      -p <pass>   Password to generate for the user (default ${PasswdDefault})"
    echo "      -f <first>  First name of the new account (default ${FirstDefault})"
    echo "      -l <last>   Last name of the new account (default ${LastDefault})"
    echo "      -v          Make it verbose!"
    echo ""
    echo "Create a new user for test purposes by signing them up through the web flow."
    echo "NOTE this is fragile as we modify the web signup flow, but on the other hand"
    echo "it's linear and easy to keep updated."
    echo ""
}

DieUsage()
{
    Usage "ERROR: $@"
    exit 1
}

Die()
{
    echo $@
    exit 2
}

function DoArgs()
{
    while getopts "d:f:l:p:u:vw:" OPTION
    do
        case $OPTION in
        d)  DbHost=$OPTARG ;;
        f)  First=$OPTARG ;;
        l)  Last=$OPTARG ;;
        p)  Passwd=$OPTARG ;;
        u)  Userid=$OPTARG ;;
        w)  WebHost=$OPTARG ;;
        v)  Verbose=1
            CurlOutput=""
             ;;
        *) DieUsage "Unrecognized argument $OPTARG" ;;
        esac
    done

    [ -n ${Passwd} ] || DieUsage "Hey tough guy, you need a password!"
    [ -n ${Userid} ] || DieUsage "UserId required! Who do you want me to create?"
}

GetCode()
{
    [ $Verbose -eq 0 ] || set -x
    QueryStr="select t_code from sp_signup_code where t_to = '${Userid}' order by t_ts desc limit 1 "
    # Arguably this shouldn't work without a 'sudo' but it sure do. What the what?
    echo "$QueryStr" | ssh -l ${DbUser} ${DbHost} mysql -N -u root -h localhost aerofs_sp \
             || Die "mysql error getting the invite code"
}

# $1 : Signup code
CreateAccount()
{
    [ $Verbose -eq 0 ] || set -x
    Code=$1
    curl -s ${CurlOutput} \
        -d "email=${Userid}" -d "c=${Code}" \
        -d "first_name=Jon" -d "last_name=Testo" -d "password=${Passwd}" \
        -d "title=" -d "company=" -d "company_size=" -d "country=" -d "phone=" \
        https://${WebHost}/json.signup \
             || Die "Curl reported an error creating the account"

}

DoInvite()
{
    [ $Verbose -eq 0 ] || set -x
    curl -s -G ${CurlOutput} \
        --data-urlencode "email=${Userid}" \
        "https://${WebHost}/json.request_to_signup" \
             || Die "Curl reported an error creating the account"
}

function Main()
{
    DoArgs $@
    [ $Verbose -eq 0 ] || set -x

    DoInvite
    Code=$(GetCode)
    CreateAccount $Code

    echo -e "Created account:\n\t${First} ${Last}\n\t${Userid}\n\t${Passwd}"
}

set -e
Main "$@";

