#!/bin/bash

typeset -a FileList
typeset -a UrlList
typeset -i Verbose=0

typeset InstallDir=AeroFSExec
typeset SiteConfigInf=AeroEnterprise.inf

typeset Extractor=WinInstaller.sfx
typeset ExtractorConfig=InstallerConfig.txt
typeset ExeFile=AeroFSInstall.exe
typeset OutFile=AeroEnterprise.exe

typeset TmpDir=$(mktemp -d -t installer_XXXXXX)

Die()
{
    echo "$@"
    exit 1
}

DieUsage()
{
    echo -e "USAGE:"
    echo -e "  build_installer.sh [-f file_res ]* [-u URI_res]*"
    echo -e "\t[-c cfgfile] [-o out_file] [-i installdir] [-x installer executable] [-v]"
    echo ""
    echo -e "\tfile_res:\tpath to a resource to be added to the final"
    echo -e "\t\t\tinstaller (no defaults)"
    echo -e "\tURI_res:\tURI to a web resource to download; the file "
    echo -e "\t\t\twill be added to the final installer (no defaults)"
    echo ""
    echo -e "\tcfgfile:\tpath to a configuration file for the SFX"
    echo -e "\t\t\textractor (default $ExtractorConfig)."
    echo -e "\t\t\tSee p7zip docs for format."
    echo -e "\tout_file:\toutput filename (default $OutFile)"
    echo -e "\tinstalldir:\tdirectory name within LocalAppData (default $InstallDir)"
    echo ""
    echo "Builds an Enterprise installer out of a SFX preamble, configuration, and"
    echo "set of file resources."
    echo ""
    echo -e "EXAMPLE:"
    echo -e "    build_installer.sh \\"
    echo -e "\t-i AeroFSExec \\"
    echo -e "\t-f site-config.properties \\"
    echo -e "\t-u https://nocache.staging.client.aerofs.com/AeroFSInstall-v0.4.222.exe \\"
    echo -e "\t-x AeroFSInstall-v0.4.222.exe \\"
    echo -e "\t-o AeroEnterprise.exe"
    echo ""
    Die "$@"
}

DoArgs()
{
    while getopts "c:f:hi:o:u:vx:" OPTION
    do
        case $OPTION in
        c)
            ExtractorConfig=$OPTARG
            ;;
        f)
            FileList+=($OPTARG)
            ;;
        h)
            DieUsage ""
            ;;
        i)
            InstallDir=$OPTARG
            ;;
        o)
            OutFile=$OPTARG
            ;;
        u)
            UrlList+=($OPTARG)
            ;;
        v)
            Verbose=1
            ;;
        x)
            ExeFile=$OPTARG
            ;;
        esac
    done
}

# TODO: I'm unhappy with all the obvious ways to clean up the downloaded artifact.
GetUrls()
{
    [ $Verbose -eq 1 ] && set -x
    set -e

    for url in ${UrlList[@]}
    do
        ufile=${url##*/}
        echo "Adding $ufile to file list..."
        FileList+=(${ufile})
        wget --no-check-certificate -N $url
    done
}

BuildArchive()
{
    [ $Verbose -eq 1 ] && set -x
    set -e

    typeset Archive=${TmpDir}/AeroFS.7z

    GetUrls

    sed "s/REPLACEME/${InstallDir}/g" $SiteConfigInf >> ${TmpDir}/$SiteConfigInf

    7z a $Archive -mx=0 ${TmpDir}/$SiteConfigInf ${FileList[@]}
    cat $Extractor > $OutFile
    sed "s/AeroFSInstall.exe/${ExeFile}/g" $ExtractorConfig >> $OutFile
    cat $Archive >> $OutFile
    rm $Archive ${TmpDir}/$SiteConfigInf
}

Main()
{
    DoArgs "$@";

    type 7z > /dev/null 2>&1 || Die "7z not found; please install p7zip"
    if [ ${#FileList[@]} -eq 0 -a ${#UrlList[@]} -eq 0 ]
    then
        Die "INSTALLERS are for CLOSERS. Add some resources with -f and -u parameters."
    fi

    BuildArchive

    rm -rf ${TmpDir}

    return 0
}

Main "$@";
