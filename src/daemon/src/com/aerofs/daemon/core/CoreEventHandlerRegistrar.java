/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.admin.HdCreateSeedFile;
import com.aerofs.daemon.core.admin.HdDeleteACL;
import com.aerofs.daemon.core.admin.HdDeleteRevision;
import com.aerofs.daemon.core.admin.HdDumpStat;
import com.aerofs.daemon.core.admin.HdExportConflict;
import com.aerofs.daemon.core.admin.HdExportFile;
import com.aerofs.daemon.core.admin.HdExportRevision;
import com.aerofs.daemon.core.admin.HdGetACL;
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
import com.aerofs.daemon.core.net.HdGetTransferStat;
import com.aerofs.daemon.core.admin.HdUpdateACL;
import com.aerofs.daemon.core.fs.HdCreateObject;
import com.aerofs.daemon.core.fs.HdDeleteBranch;
import com.aerofs.daemon.core.fs.HdDeleteObject;
import com.aerofs.daemon.core.fs.HdGetAttr;
import com.aerofs.daemon.core.fs.HdGetChildrenAttr;
import com.aerofs.daemon.core.fs.HdImportFile;
import com.aerofs.daemon.core.fs.HdJoinSharedFolder;
import com.aerofs.daemon.core.fs.HdLeaveSharedFolder;
import com.aerofs.daemon.core.fs.HdListSharedFolders;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.fs.HdSetAttr;
import com.aerofs.daemon.core.fs.HdShareFolder;
import com.aerofs.daemon.core.net.HdPresence;
import com.aerofs.daemon.core.net.HdPulseStopped;
import com.aerofs.daemon.core.net.HdTransportMetricsUpdated;
import com.aerofs.daemon.core.net.HdChunk;
import com.aerofs.daemon.core.net.HdMaxcastMessage;
import com.aerofs.daemon.core.net.HdStreamAborted;
import com.aerofs.daemon.core.net.HdStreamBegun;
import com.aerofs.daemon.core.net.HdUnicastMessage;
import com.aerofs.daemon.core.status.HdGetStatusOverview;
import com.aerofs.daemon.core.syncstatus.HdGetSyncStatus;
import com.aerofs.daemon.core.test.HdTestGetAliasObject;
import com.aerofs.daemon.event.admin.EICreateSeedFile;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.admin.EIDeleteRevision;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.admin.EIExportConflict;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.admin.EIGetTransferStat;
import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.admin.EIInvalidateDeviceNameCache;
import com.aerofs.daemon.event.admin.EIInvalidateUserNameCache;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.admin.EILeaveSharedFolder;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.admin.EIListSharedFolderInvitations;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.fs.EICreateObject;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.fs.EIImportFile;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.fs.EISetAttr;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EIPulseStopped;
import com.aerofs.daemon.event.net.EITransportMetricsUpdated;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.event.net.rx.EIStreamAborted;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.event.test.EITestGetAliasObject;
import com.aerofs.daemon.mobile.EIDownloadPacket;
import com.aerofs.daemon.mobile.HdDownloadPacket;

import com.google.inject.Inject;

public class CoreEventHandlerRegistrar implements ICoreEventHandlerRegistrar
{
    private final HdCreateObject _hdco;
    private final HdMoveObject _hdMoveObject;
    private final HdDeleteObject _hddo;
    private final HdDeleteBranch _hddb;
    private final HdJoinSharedFolder _hdJoinSharedFolder;
    private final HdLeaveSharedFolder _hdLeaveSharedFolder;
    private final HdListSharedFolders _hdListSharedFolders;
    private final HdListSharedFolderInvitations _hdListSharedFolderInvitations;
    private final HdSetExpelled _hdSetExpelled;
    private final HdListExpelledObjects _hdListExpelledObjects;
    private final HdPulseStopped _hdPulseStopped;
    private final HdStreamAborted _hdStreamAborted;
    private final HdChunk _hdChunk;
    private final HdStreamBegun _hdStreamBegun;
    private final HdMaxcastMessage _hdMaxcastMessage;
    private final HdUnicastMessage _hdUnicastMessage;
    private final HdTransportMetricsUpdated _hdTransportMetricsUpdated;
    private final HdPresence _hdPresence;
    private final HdListConflicts _hdListConflicts;
    private final HdExportConflict _hdExportConflict;
    private final HdGetTransferStat _hdGetTransferStat;
    private final HdDumpStat _hdDumpStat;
    private final HdReloadConfig _hdReloadConfig;
    private final HdPauseOrResumeSyncing _hdPauseOrResumeSyncing;
    private final HdGetACL _hdGetACL;
    private final HdUpdateACL _hdUpdateACL;
    private final HdDeleteACL _hdDeleteACL;
    private final HdSetAttr _hdSetAttr;
    private final HdGetChildrenAttr _hdGetChildrenAttr;
    private final HdGetAttr _hdGetAttr;
    private final HdShareFolder _hdShareFolder;
    private final HdImportFile _hdImportFile;
    private final HdExportFile _hdExportFile;
    private final HdRelocateRootAnchor _hdRelocateRootAnchor;
    private final HdListRevChildren _hdListRevChildren;
    private final HdListRevHistory _hdListRevHistory;
    private final HdExportRevision _hdExportRevision;
    private final HdDeleteRevision _hdDeleteRevision;
    private final HdGetSyncStatus _hdGetSyncStatus;
    private final HdGetStatusOverview _hdGetStatusOverview;
    private final HdHeartbeat _hdHeartbeat;
    private final HdGetActivities _hdGetActivities;
    private final HdDownloadPacket _hdDownloadPacket;
    private final HdInvalidateUserNameCache _hdInvalidateUserNameCache;
    private final HdInvalidateDeviceNameCache _hdInvalidateDeviceNameCache;
    private final HdCreateSeedFile _hdCreateSeedFile;
    private final HdTestGetAliasObject _hdTestGetAliasObject;

    @Inject
    public CoreEventHandlerRegistrar(HdCreateObject hdco, HdMoveObject hdMoveObject,
            HdDeleteObject hddo, HdDeleteBranch hddb, HdJoinSharedFolder hdJoinSharedFolder,
            HdSetExpelled hdSetExpelled, HdListSharedFolders hdListSharedFolders,
            HdListSharedFolderInvitations hdListSharedFolderInvitations,
            HdListExpelledObjects hdListExpelledObjects, HdPulseStopped hdPulseStopped,
            HdStreamAborted hdStreamAborted, HdChunk hdChunk, HdStreamBegun hdStreamBegun,
            HdMaxcastMessage hdMaxcastMessage, HdUnicastMessage hdUnicastMessage,
            HdTransportMetricsUpdated hdTransportMetricsUpdated,
            HdPresence hdPresence, HdListConflicts hdListConflicts,
            HdExportConflict hdExportConflict,
            HdDumpStat hdDumpStat, HdReloadConfig hdReloadConfig,
            HdPauseOrResumeSyncing hdPauseOrResumeSyncing, HdGetACL hdGetACL,
            HdUpdateACL hdUpdateACL, HdDeleteACL hdDeleteACL, HdSetAttr hdSetAttr,
            HdGetChildrenAttr hdGetChildrenAttr, HdGetAttr hdGetAttr, HdShareFolder hdShareFolder,
            HdImportFile hdImportFile, HdExportFile hdExportFile,
            HdRelocateRootAnchor hdRelocateRootAnchor, HdListRevChildren hdListRevChildren,
            HdListRevHistory hdListRevHistory, HdExportRevision hdExportRevision,
            HdDeleteRevision hdDeleteRevision,
            HdGetSyncStatus hdGetSyncStatus, HdGetStatusOverview hdGetStatusOverview,
            HdHeartbeat hdHeartbeat, HdGetActivities hdGetActivities,
            HdDownloadPacket hdDownloadPacket, HdLeaveSharedFolder hdLeaveSharedFolder,
            HdInvalidateUserNameCache hdInvalidateUserNameCache,
            HdInvalidateDeviceNameCache hdInvalidateDeviceNameCache,
            HdCreateSeedFile hdCreateSeedFile, HdGetTransferStat hdGetTransferStat,
            HdTestGetAliasObject hdTestGetAliasObject)
    {
        _hdco = hdco;
        _hdMoveObject = hdMoveObject;
        _hddo = hddo;
        _hddb = hddb;
        _hdJoinSharedFolder = hdJoinSharedFolder;
        _hdLeaveSharedFolder = hdLeaveSharedFolder;
        _hdListSharedFolders = hdListSharedFolders;
        _hdListSharedFolderInvitations = hdListSharedFolderInvitations;
        _hdSetExpelled = hdSetExpelled;
        _hdListExpelledObjects = hdListExpelledObjects;
        _hdPulseStopped = hdPulseStopped;
        _hdStreamAborted = hdStreamAborted;
        _hdChunk = hdChunk;
        _hdStreamBegun = hdStreamBegun;
        _hdMaxcastMessage = hdMaxcastMessage;
        _hdUnicastMessage = hdUnicastMessage;
        _hdTransportMetricsUpdated = hdTransportMetricsUpdated;
        _hdPresence = hdPresence;
        _hdListConflicts = hdListConflicts;
        _hdExportConflict = hdExportConflict;
        _hdDumpStat = hdDumpStat;
        _hdReloadConfig = hdReloadConfig;
        _hdPauseOrResumeSyncing = hdPauseOrResumeSyncing;
        _hdGetACL = hdGetACL;
        _hdUpdateACL = hdUpdateACL;
        _hdDeleteACL = hdDeleteACL;
        _hdSetAttr = hdSetAttr;
        _hdGetChildrenAttr = hdGetChildrenAttr;
        _hdGetAttr = hdGetAttr;
        _hdShareFolder = hdShareFolder;
        _hdImportFile = hdImportFile;
        _hdExportFile = hdExportFile;
        _hdRelocateRootAnchor = hdRelocateRootAnchor;
        _hdListRevChildren = hdListRevChildren;
        _hdListRevHistory = hdListRevHistory;
        _hdExportRevision = hdExportRevision;
        _hdDeleteRevision = hdDeleteRevision;
        _hdGetSyncStatus = hdGetSyncStatus;
        _hdGetStatusOverview = hdGetStatusOverview;
        _hdHeartbeat = hdHeartbeat;
        _hdGetActivities = hdGetActivities;
        _hdDownloadPacket = hdDownloadPacket;
        _hdInvalidateUserNameCache = hdInvalidateUserNameCache;
        _hdInvalidateDeviceNameCache = hdInvalidateDeviceNameCache;
        _hdCreateSeedFile = hdCreateSeedFile;
        _hdGetTransferStat = hdGetTransferStat;
        _hdTestGetAliasObject = hdTestGetAliasObject;
    }

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
                .setHandler_(EIGetACL.class, _hdGetACL)
                .setHandler_(EIUpdateACL.class, _hdUpdateACL)
                .setHandler_(EIDeleteACL.class, _hdDeleteACL)
                .setHandler_(EISetAttr.class, _hdSetAttr)

                // admin events
                .setHandler_(EIReloadConfig.class, _hdReloadConfig)
                .setHandler_(EIDumpStat.class, _hdDumpStat)
                .setHandler_(EIShareFolder.class, _hdShareFolder)
                .setHandler_(EIJoinSharedFolder.class, _hdJoinSharedFolder)
                .setHandler_(EILeaveSharedFolder.class, _hdLeaveSharedFolder)
                .setHandler_(EIListSharedFolders.class, _hdListSharedFolders)
                .setHandler_(EIListSharedFolderInvitations.class, _hdListSharedFolderInvitations)
                .setHandler_(EIListConflicts.class, _hdListConflicts)
                .setHandler_(EIExportConflict.class, _hdExportConflict)
                .setHandler_(EISetExpelled.class, _hdSetExpelled)
                .setHandler_(EIListExpelledObjects.class, _hdListExpelledObjects)
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
                .setHandler_(EIGetSyncStatus.class, _hdGetSyncStatus)
                .setHandler_(EIGetStatusOverview.class, _hdGetStatusOverview)

                // net events
                .setHandler_(EIPresence.class, _hdPresence)
                .setHandler_(EITransportMetricsUpdated.class, _hdTransportMetricsUpdated)
                .setHandler_(EIUnicastMessage.class, _hdUnicastMessage)
                .setHandler_(EIMaxcastMessage.class, _hdMaxcastMessage)
                .setHandler_(EIStreamBegun.class, _hdStreamBegun)
                .setHandler_(EIChunk.class, _hdChunk)
                .setHandler_(EIStreamAborted.class, _hdStreamAborted)
                .setHandler_(EIPulseStopped.class, _hdPulseStopped)

                // mobile events
                .setHandler_(EIDownloadPacket.class, _hdDownloadPacket)

                // test events
                .setHandler_(EITestGetAliasObject.class, _hdTestGetAliasObject)
                ;
    }
}
