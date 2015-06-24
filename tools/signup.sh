#!/bin/bash

# This script is very identical to the tools/sctest/clients/signup.sh script
# If you need to change this script(except the variables below) then please
# make sure to check if the tools/sctest/clients/signup.sh script also needs to be changed.
# Note: This script is used to signup a client on your local prod.
FirstDefault=Firsty
First=$FirstDefault
LastDefault=Lasto
Last=$LastDefault
PasswdDefault=uiuiui
Passwd=$PasswdDefault

CurlOutput="-o /dev/null"
WebHostDefault=share.syncfs.com
WebHost=$WebHostDefault
UseridDefault=
Userid=
Verbose=0

declare -i AutoSignup=0

Usage()
{
    [ $# -gt 0 ] && echo -e "\n$@\n"
    echo "$0 [-a] [-w webhost ] [-p password]"
    echo "$0 [-v] [-w webhost ] [-f first] [-l last] -u userid -p password"
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

    AllCodes=$(curl -s http://${WebHost}:21337/get_all_codes)
    NumUsers=$(echo $AllCodes | jq '.signup_codes | length')

    for i in $(seq 0 1 $(($NumUsers - 1)))
    do
        $user=$(echo $AllCodes | jq -r '.signup_codes[$i].user')
        $code=$(echo $AllCodes | jq -r '.signup_codes[$i].signup_code')
        echo "Found user $user $code"
        CreateAccount $code $user
    done
}

GetCode()
{
    [ $Verbose -eq 0 ] || set -x

    JsonCode=$(curl -X GET -G -s --data-urlencode "userid=${Userid}" http://${WebHost}:21337/get_code)
    echo $JsonCode | jq -r '.signup_code'
}

# $1 : Signup code
# $2 : User email
CreateAccount()
{
    [ $Verbose -eq 0 ] || set -x
    Code=$1
    User=$2

    if [ -z $Code ] || [ -z $User ]
    then
        Die "Tried to create an account with empty code or userid"
    fi

    # Storing cookies and CsrfTokens to use later when creating an account.
    CookieJar=$(mktemp -t cookieXXXXXX)
    CsrfToken=$(curl -s https://${WebHost}/setup -k -c ${CookieJar} | grep csrf-token | awk -F 'content="' '{print $2}' | awk -F '"' '{print $1}')
    curl --fail -k \
        -s ${CurlOutput} \
        --data-urlencode "email=${User}" -d "c=${Code}" \
        -d "first_name=Jon" -d "last_name=Testo" -d "password=${Passwd}" \
        -d "csrf_token=${CsrfToken}" -b ${CookieJar} \
        https://${WebHost}/json.signup \
             || Die "Curl reported an error creating the account"
    rm $CookieJar
}

DoInvite()
{
    [ $Verbose -eq 0 ] || set -x
    curl --fail -s -G ${CurlOutput} \
        --data-urlencode "email=${Userid}" \
        "https://${WebHost}/json.request_to_signup" \
             || Die "Curl reported an error signing up for the account"
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

