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
DbUser=ubuntu
UseridDefault=
Userid=
Verbose=0

declare -i AutoSignup=0

Usage()
{
    [ $# -gt 0 ] && echo -e "\n$@\n"
    echo "$0 [-a] [-w webhost ] [ -d dbhost ] [-p password]"
    echo "$0 [-v] [-w webhost ] [ -d dbhost ] [-f first] [-l last] -u userid -p password"
    echo ""
    echo "      -a          Auto-sign-up all invited users in the signup_code table."
    echo "                  (useful if you have invitation-only set, invite users from web UI,"
    echo "                   then use auto-signup)"
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
    Usage "$@"
    exit 1
}

Die()
{
    echo $@
    exit 2
}

function DoArgs()
{
    while getopts "ad:f:l:p:u:vw:" OPTION
    do
        case $OPTION in
        a)  AutoSignup=1 ;;
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

    test -n ${Passwd} || DieUsage "Hey tough guy, you need a password!"
    test -n "${Userid}" -o $AutoSignup != 0 || DieUsage "Who do you want me to create?"
}

SignupAll()
{
    [ $Verbose -eq 0 ] || set -x
    QueryStr="select t_code, t_to from sp_signup_code order by t_ts"

    echo "$QueryStr" \
        | ssh -i ~/.ssh/id_rsa -o StrictHostKeyChecking=no -l ${DbUser} ${DbHost} mysql -N -u root -h localhost aerofs_sp \
        | while read code user
        do
        # Arguably this shouldn't work without a 'sudo' but it sure do. What the what?
            echo "Found user $user $code"
            CreateAccount $code $user
        done
}

GetCode()
{
    [ $Verbose -eq 0 ] || set -x
    QueryStr="select t_code from sp_signup_code where t_to = '${Userid}' order by t_ts desc limit 1 "
    # Arguably this shouldn't work without a 'sudo' but it sure do. What the what?
    echo "$QueryStr" | ssh -i ~/.ssh/id_rsa -o StrictHostKeyChecking=no -l ${DbUser} ${DbHost} mysql -N -u root -h localhost aerofs_sp \
             || Die "mysql error getting the invite code"
}

# $1 : Signup code
# $2 : User email
CreateAccount()
{
    [ $Verbose -eq 0 ] || set -x
    Code=$1
    User=$2
    curl -k -s ${CurlOutput} \
        -d "email=${User}" -d "c=${Code}" \
        -d "first_name=Jon" -d "last_name=Testo" -d "password=${Passwd}" \
        https://${WebHost}/json.signup \
             || Die "Curl reported an error creating the account"

}

DoInvite()
{
    [ $Verbose -eq 0 ] || set -x
    curl -k -s -G ${CurlOutput} \
        --data-urlencode "email=${Userid}" \
        "https://${WebHost}/json.request_to_signup" \
             || Die "Curl reported an error creating the account"
}

function Main()
{
    
    DoArgs $@
    [ $Verbose -eq 0 ] || set -x
    if [ $AutoSignup -ne 0 ] ; then
        SignupAll
        return $?
    fi
    DoInvite
    Code=$(GetCode)
    CreateAccount $Code $Userid
    echo -e "Created account:\n\t${First} ${Last}\n\t${Userid}\n\t${Passwd}"
}

set -e
Main "$@";