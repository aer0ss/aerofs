/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.admin.HdCreateSeedFile;
import com.aerofs.daemon.core.admin.HdDeleteACL;
import com.aerofs.daemon.core.admin.HdDeleteRevision;
import com.aerofs.daemon.core.admin.HdDumpDiagnostics;
import com.aerofs.daemon.core.admin.HdDumpStat;
import com.aerofs.daemon.core.admin.HdExportConflict;
import com.aerofs.daemon.core.admin.HdExportFile;
import com.aerofs.daemon.core.admin.HdExportRevision;
import com.aerofs.daemon.core.admin.HdGetActivities;
import com.aerofs.daemon.core.admin.HdHeartbeat;
import com.aerofs.daemon.core.admin.HdInvalidateDeviceNameCache;
import com.aerofs.daemon.core.admin.HdInvalidateUserNameCache;
import com.aerofs.daemon.core.admin.HdListConflicts;
import com.aerofs.daemon.core.admin.HdListExpelledObjects;
import com.aerofs.daemon.core.admin.HdListRevChildren;
import com.aerofs.daemon.core.admin.HdListRevHistory;
import com.aerofs.daemon.core.admin.HdListSharedFolderInvitations;
import com.aerofs.daemon.core.admin.HdPauseOrResumeSyncing;
import com.aerofs.daemon.core.admin.HdReloadConfig;
import com.aerofs.daemon.core.admin.HdRelocateRootAnchor;
import com.aerofs.daemon.core.admin.HdSetExpelled;
import com.aerofs.daemon.core.admin.HdUpdateACL;
import com.aerofs.daemon.core.fs.HdCreateUrl;
import com.aerofs.daemon.core.fs.HdListSharedFolders;
import com.aerofs.daemon.core.fs.HdCreateObject;
import com.aerofs.daemon.core.fs.HdDeleteBranch;
import com.aerofs.daemon.core.fs.HdDeleteObject;
import com.aerofs.daemon.core.fs.HdGetAttr;
import com.aerofs.daemon.core.fs.HdGetChildrenAttr;
import com.aerofs.daemon.core.fs.HdImportFile;
import com.aerofs.daemon.core.fs.HdListNonRepresentableObjects;
import com.aerofs.daemon.core.fs.HdListUserRoots;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.fs.HdShareFolder;
import com.aerofs.daemon.core.net.HdGetTransferStat;
import com.aerofs.daemon.core.quota.EITestCheckQuota;
import com.aerofs.daemon.core.quota.HdTestCheckQuota;
import com.aerofs.daemon.core.status.HdGetStatusOverview;
import com.aerofs.daemon.core.test.HdTestGetAliasObject;
import com.aerofs.daemon.event.admin.EICreateSeedFile;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.admin.EIDeleteRevision;
import com.aerofs.daemon.event.admin.EIDumpDiagnostics;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.admin.EIExportConflict;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.admin.EIGetTransferStat;
import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.admin.EIInvalidateDeviceNameCache;
import com.aerofs.daemon.event.admin.EIInvalidateUserNameCache;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.admin.EIListSharedFolderInvitations;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.admin.EIListUserRoots;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.fs.EICreateObject;
import com.aerofs.daemon.event.fs.EICreateUrl;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.fs.EIImportFile;
import com.aerofs.daemon.event.fs.EIListNonRepresentableObjects;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.daemon.event.test.EITestGetAliasObject;
import com.google.inject.Inject;

public class CoreEventHandlerRegistrar implements ICoreEventHandlerRegistrar
{
    @Inject HdCreateObject _hdco;
    @Inject HdMoveObject _hdMoveObject;
    @Inject HdDeleteObject _hddo;
    @Inject HdDeleteBranch _hddb;
    @Inject HdListSharedFolders _hdListSharedFolders;
    @Inject HdListUserRoots _hdListUserRoots;
    @Inject HdListSharedFolderInvitations _hdListSharedFolderInvitations;
    @Inject HdSetExpelled _hdSetExpelled;
    @Inject HdListExpelledObjects _hdListExpelledObjects;
    @Inject HdListNonRepresentableObjects _hdListNonRepresentableObjects;
    @Inject HdListConflicts _hdListConflicts;
    @Inject HdExportConflict _hdExportConflict;
    @Inject HdGetTransferStat _hdGetTransferStat;
    @Inject HdDumpStat _hdDumpStat;
    @Inject HdDumpDiagnostics _hdDumpDiagnostics;
    @Inject HdReloadConfig _hdReloadConfig;
    @Inject HdPauseOrResumeSyncing _hdPauseOrResumeSyncing;
    @Inject HdUpdateACL _hdUpdateACL;
    @Inject HdDeleteACL _hdDeleteACL;
    @Inject HdGetChildrenAttr _hdGetChildrenAttr;
    @Inject HdGetAttr _hdGetAttr;
    @Inject HdCreateUrl _hdCreateUrl;
    @Inject HdShareFolder _hdShareFolder;
    @Inject HdImportFile _hdImportFile;
    @Inject HdExportFile _hdExportFile;
    @Inject HdRelocateRootAnchor _hdRelocateRootAnchor;
    @Inject HdListRevChildren _hdListRevChildren;
    @Inject HdListRevHistory _hdListRevHistory;
    @Inject HdExportRevision _hdExportRevision;
    @Inject HdDeleteRevision _hdDeleteRevision;
    @Inject HdGetStatusOverview _hdGetStatusOverview;
    @Inject HdHeartbeat _hdHeartbeat;
    @Inject HdGetActivities _hdGetActivities;
    @Inject HdInvalidateUserNameCache _hdInvalidateUserNameCache;
    @Inject HdInvalidateDeviceNameCache _hdInvalidateDeviceNameCache;
    @Inject HdCreateSeedFile _hdCreateSeedFile;
    @Inject HdTestGetAliasObject _hdTestGetAliasObject;
    @Inject HdTestCheckQuota _hdTestCheckQuota;

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        // TODO (WW) each subsystem should implement their own ICoreEventHandlerRegistrar

        disp
                // fs events
                .setHandler_(EICreateObject.class, _hdco)
                .setHandler_(EIMoveObject.class, _hdMoveObject)
                .setHandler_(EIDeleteObject.class, _hddo)
                .setHandler_(EIDeleteBranch.class, _hddb)
                .setHandler_(EIGetAttr.class, _hdGetAttr)
                .setHandler_(EIGetChildrenAttr.class, _hdGetChildrenAttr)
                .setHandler_(EIUpdateACL.class, _hdUpdateACL)
                .setHandler_(EIDeleteACL.class, _hdDeleteACL)

                // admin events
                .setHandler_(EIReloadConfig.class, _hdReloadConfig)
                .setHandler_(EIDumpStat.class, _hdDumpStat)
                .setHandler_(EIDumpDiagnostics.class, _hdDumpDiagnostics)
                .setHandler_(EICreateUrl.class, _hdCreateUrl)
                .setHandler_(EIShareFolder.class, _hdShareFolder)
                .setHandler_(EIListSharedFolders.class, _hdListSharedFolders)
                .setHandler_(EIListUserRoots.class, _hdListUserRoots)
                .setHandler_(EIListSharedFolderInvitations.class, _hdListSharedFolderInvitations)
                .setHandler_(EIListConflicts.class, _hdListConflicts)
                .setHandler_(EIExportConflict.class, _hdExportConflict)
                .setHandler_(EISetExpelled.class, _hdSetExpelled)
                .setHandler_(EIListExpelledObjects.class, _hdListExpelledObjects)
                .setHandler_(EIListNonRepresentableObjects.class, _hdListNonRepresentableObjects)
                .setHandler_(EIPauseOrResumeSyncing.class, _hdPauseOrResumeSyncing)
                .setHandler_(EIImportFile.class, _hdImportFile)
                .setHandler_(EIExportFile.class, _hdExportFile)
                .setHandler_(EIRelocateRootAnchor.class, _hdRelocateRootAnchor)
                .setHandler_(EIListRevChildren.class, _hdListRevChildren)
                .setHandler_(EIListRevHistory.class, _hdListRevHistory)
                .setHandler_(EIExportRevision.class, _hdExportRevision)
                .setHandler_(EIDeleteRevision.class, _hdDeleteRevision)
                .setHandler_(EIHeartbeat.class, _hdHeartbeat)
                .setHandler_(EIGetActivities.class, _hdGetActivities)
                .setHandler_(EIInvalidateUserNameCache.class, _hdInvalidateUserNameCache)
                .setHandler_(EIInvalidateDeviceNameCache.class, _hdInvalidateDeviceNameCache)
                .setHandler_(EICreateSeedFile.class, _hdCreateSeedFile)
                .setHandler_(EIGetTransferStat.class, _hdGetTransferStat)

                // status events
                .setHandler_(EIGetStatusOverview.class, _hdGetStatusOverview)

                // test events
                .setHandler_(EITestGetAliasObject.class, _hdTestGetAliasObject)
                .setHandler_(EITestCheckQuota.class, _hdTestCheckQuota)
                ;
    }
}
