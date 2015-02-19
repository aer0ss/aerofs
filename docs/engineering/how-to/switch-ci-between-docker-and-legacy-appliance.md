## Switch CI to Dockerized appliance

[Edit Build Step](https://newci.arrowfs.org:8543/admin/editBuildRunners.html?id=buildType:AeroFS_SystemTestsFullCiRun_PostSystemTests) of AeroFS > CI > Archive Appliance Logs. Change the following options of the only build step from:

    Command executable: tools/newci/archive_logs.sh
    Command parameters: (empty)

To:

    Command executable: docker/ci/collect-appliance-logs.sh
    Command parameters: appliance-logs.tgz
    
[Edit General Settings](https://newci.arrowfs.org:8543/admin/editBuild.html?id=buildType:AeroFS_SystemTestsFullCiRun_PostSystemTests) of the same build configuration, and change from:

    Artifact paths: appliance-logs.zip
    
To:

    Artifact paths: appliance-logs.tgz

[Edit Dependencies](https://newci.arrowfs.org:8543/admin/editDependencies.html?id=template:AeroFS_SystemTests) of AeroFS > CI > System Tests template. Change both snapshot dependency and artifact dependencies from:

    AeroFS :: Legacy :: Private Deployment Setup
    
To:

    AeroFS :: Docker :: Configure Appliance
    
N.B. Please keep other options unchanged. Do not delete and recreate dependencies. It would destroy these options.

[Edit Dependencies](https://newci.arrowfs.org:8543/admin/editDependencies.html?id=template:AeroFS_SystemTestsFullCiRun_TeamServer) of AeroFS > CI > Team Server System Tests template. Update it exactly the same way as the System Tests template above.


## Switch CI to legacy appliance

Reverse all the steps above.