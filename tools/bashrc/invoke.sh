#!/bin/bash

GIT_ROOT=$(dirname $(dirname $(dirname "${BASH_SOURCE[0]:-$0}")))
alias invoke="$GIT_ROOT/invoke"

# The _filedir function does intelligent bash completion for files and folders.
# However, it is part of the optional package bash-completion, which some users
# may not have installed.  Guard calls to _filedir accordingly and use the much
# less featureful/useful compgen options if not available.
__invoke_filedir()
{
    declare -f -F _filedir > /dev/null
    if [[ $? -eq 0 ]] ; then
        _filedir "$@"
    else
        if [[ $1 == "-d" ]] ; then
            COMPREPLY=( $( compgen -d -- ${COMP_WORDS[COMP_CWORD]}) )
        else
            COMPREPLY=( $( compgen -f -- ${COMP_WORDS[COMP_CWORD]}) )
        fi
    fi
}

_invoke()
{
    local cur prev opts commands
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    # Keep these up to date with the options invoke supports.
    opts="--approot --product --release-version --signed --unsigned --syncdet-case --syncdet-case-timeout --syncdet-config --syncdet-executable --syncdet-extra-args --syncdet-scenario --syncdet-sync-timeout --syncdet-transport --target-os --team-city"
    commands="build_client build_sa build_protoc_plugins build_docker_images build_sa_images build_vm build_sa_vm build_cloud_config push_docker_images push_vm tag_release clean markdown markdown_watch package_clients prepare_syncdet proto setupenv test_js test_python test_system test_system_archive write_version"

    # If the previous arg is one of these, give context specific completion options
    case "${prev}" in
        --syncdet-config|--syncdet-executable|--syncdet-scenario)
            __invoke_filedir
            return 0
            ;;
        --syncdet-case|--syncdet-case-timeout|--syncdet-extra-args|--syncdet-sync-timeout|--release-version)
            # No completion, don't suggest anything
            return 0
            ;;
        --product)
            local products="CLIENT TEAM_SERVER"
            COMPREPLY=( $(compgen -W "${products}" -- ${cur}) )
            return 0
            ;;
        --syncdet-transport)
            local transports="default tcp zephyr"
            COMPREPLY=( $(compgen -W "${transports}" -- ${cur}) )
            return 0
            ;;
        --target-os)
            local oses="win osx linux32 linux64"
            COMPREPLY=( $(compgen -W "${oses}" -- ${cur}) )
            return 0
            ;;
    esac

    # If the current arg starts with a -, complete with the known options
    # otherwise, complete with the known commands
    if [[ ${cur} == -* ]] ; then
        COMPREPLY=( $(compgen -W "${opts}" -- ${cur}) )
    else
        COMPREPLY=( $(compgen -W "${commands}" -- ${cur}) )
    fi
}

# allow zsh to have the same cool autocompletion features as bash
command -v autoload &>/dev/null
if [ $? -eq 0 ] ; then
    autoload bashcompinit
    bashcompinit
fi

# turn on autocompletion
complete -F _invoke invoke
