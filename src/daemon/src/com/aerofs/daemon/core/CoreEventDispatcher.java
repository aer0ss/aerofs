package com.aerofs.daemon.core;

import javax.inject.Inject;

import com.aerofs.daemon.core.admin.HdDeleteACL;
import com.aerofs.daemon.core.admin.HdDumpStat;
import com.aerofs.daemon.core.admin.HdExportFile;
import com.aerofs.daemon.core.admin.HdExportRevision;
import com.aerofs.daemon.core.admin.HdGetACL;
import com.aerofs.daemon.core.admin.HdGetActivities;
import com.aerofs.daemon.core.admin.HdHeartbeat;
import com.aerofs.daemon.core.admin.HdListConflicts;
import com.aerofs.daemon.core.admin.HdListExpelledObjects;
import com.aerofs.daemon.core.admin.HdListRevChildren;
import com.aerofs.daemon.core.admin.HdListRevHistory;
import com.aerofs.daemon.core.admin.HdPauseOrResumeSyncing;
import com.aerofs.daemon.core.admin.HdReloadConfig;
import com.aerofs.daemon.core.admin.HdRelocateRootAnchor;
import com.aerofs.daemon.core.admin.HdSaveRevision;
import com.aerofs.daemon.core.admin.HdSetACL;
import com.aerofs.daemon.core.admin.HdSetExpelled;
import com.aerofs.daemon.core.admin.HdSetPrivateKey;
import com.aerofs.daemon.core.admin.HdTransportFlood;
import com.aerofs.daemon.core.admin.HdTransportFloodQuery;
import com.aerofs.daemon.core.admin.HdTransportPing;
import com.aerofs.daemon.core.fs.HdCreateObject;
import com.aerofs.daemon.core.fs.HdDeleteBranch;
import com.aerofs.daemon.core.fs.HdDeleteObject;
import com.aerofs.daemon.core.fs.HdGetAttr;
import com.aerofs.daemon.core.fs.HdGetChildrenAttr;
import com.aerofs.daemon.core.fs.HdJoinSharedFolder;
import com.aerofs.daemon.core.fs.HdListSharedFolders;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.fs.HdSetAttr;
import com.aerofs.daemon.core.fs.HdShareFolder;
import com.aerofs.daemon.core.net.HdPresence;
import com.aerofs.daemon.core.net.HdPulseStopped;
import com.aerofs.daemon.core.net.HdTransportMetricsUpdated;
import com.aerofs.daemon.core.net.rx.HdChunk;
import com.aerofs.daemon.core.net.rx.HdMaxcastMessage;
import com.aerofs.daemon.core.net.rx.HdSessionEnded;
import com.aerofs.daemon.core.net.rx.HdStreamAborted;
import com.aerofs.daemon.core.net.rx.HdStreamBegun;
import com.aerofs.daemon.core.net.rx.HdUnicastMessage;
import com.aerofs.daemon.core.status.HdGetStatusOverview;
import com.aerofs.daemon.core.syncstatus.HdGetSyncStatus;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.admin.EISaveRevision;
import com.aerofs.daemon.event.admin.EISetACL;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.admin.EISetPrivateKey;
import com.aerofs.daemon.event.admin.EITransportFlood;
import com.aerofs.daemon.event.admin.EITransportFloodQuery;
import com.aerofs.daemon.event.admin.EITransportPing;
import com.aerofs.daemon.event.fs.EICreateObject;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.fs.EISetAttr;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EIPulseStopped;
import com.aerofs.daemon.event.net.EITransportMetricsUpdated;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.event.net.rx.EISessionEnded;
import com.aerofs.daemon.event.net.rx.EIStreamAborted;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.status.EIGetStatusOverview;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.mobile.EIDownloadPacket;
import com.aerofs.daemon.mobile.HdDownloadPacket;

public class CoreEventDispatcher extends EventDispatcher
{
    @Inject
    public CoreEventDispatcher(
            HdCreateObject hdco,
            HdMoveObject hdMoveObject,
            HdDeleteObject hddo,
            HdDeleteBranch hddb,
            HdJoinSharedFolder hdJoinSharedFolder,
            HdListSharedFolders hdListSharedFolders,
            HdSetExpelled hdSetExpelled,
            HdListExpelledObjects hdListExpelledObjects,
            HdPulseStopped hdPulseStopped,
            HdStreamAborted hdStreamAborted,
            HdChunk hdChunk,
            HdStreamBegun hdStreamBegun,
            HdMaxcastMessage hdMaxcastMessage,
            HdUnicastMessage hdUnicastMessage,
            HdSessionEnded hdSessionEnded,
            HdTransportMetricsUpdated hdTransportMetricsUpdated,
            HdPresence hdPresence,
            HdSaveRevision hdSaveRevision,
            HdListConflicts hdListConflicts,
            HdTransportFloodQuery hdTransportFloodQuery,
            HdTransportFlood hdTransportFlood,
            HdSetPrivateKey hdSetPrivateKey,
            HdTransportPing hdTransportPing,
            HdDumpStat hdDumpStat,
            HdReloadConfig hdReloadConfig,
            HdPauseOrResumeSyncing hdPauseOrResumeSyncing,
            HdGetACL hdGetACL,
            HdSetACL hdSetACL,
            HdDeleteACL hdDeleteACL,
            HdSetAttr hdSetAttr,
            HdGetChildrenAttr hdGetChildrenAttr,
            HdGetAttr hdGetAttr,
            HdShareFolder hdShareFolder,
            HdExportFile hdExportFile,
            HdRelocateRootAnchor hdRelocateRootAnchor,
            HdListRevChildren hdListRevChildren,
            HdListRevHistory hdListRevHistory,
            HdExportRevision hdExportRevision,
            HdGetSyncStatus hdGetSyncStatus,
            HdGetStatusOverview hdGetStatusOverview,
            HdHeartbeat hdHeartbeat,
            HdGetActivities hdGetActivities,
            HdDownloadPacket hdDownloadPacket)
    {
        this
            // fs events
            .setHandler_(EICreateObject.class, hdco)
            .setHandler_(EIMoveObject.class, hdMoveObject)
            .setHandler_(EIDeleteObject.class, hddo)
            .setHandler_(EIDeleteBranch.class, hddb)
            .setHandler_(EIGetAttr.class, hdGetAttr)
            .setHandler_(EIGetChildrenAttr.class, hdGetChildrenAttr)
            .setHandler_(EIGetACL.class, hdGetACL)
            .setHandler_(EISetACL.class, hdSetACL)
            .setHandler_(EIDeleteACL.class, hdDeleteACL)
            .setHandler_(EISetAttr.class, hdSetAttr)

            // linker events are registered by Linker. Ideally, events
            // for each subsystem should be registered by that subsystem.
            // But because we're going to use Futures instead of the event
            // system, we didn't bother changing existing code

            // admin events
            .setHandler_(EIReloadConfig.class, hdReloadConfig)
            .setHandler_(EIDumpStat.class, hdDumpStat)
            .setHandler_(EISetPrivateKey.class, hdSetPrivateKey)
            .setHandler_(EIShareFolder.class, hdShareFolder)
            .setHandler_(EIJoinSharedFolder.class, hdJoinSharedFolder)
            .setHandler_(EIListSharedFolders.class, hdListSharedFolders)
            .setHandler_(EITransportPing.class, hdTransportPing)
            .setHandler_(EITransportFlood.class, hdTransportFlood)
            .setHandler_(EITransportFloodQuery.class, hdTransportFloodQuery)
            .setHandler_(EIListConflicts.class, hdListConflicts)
            .setHandler_(EISaveRevision.class, hdSaveRevision)
            .setHandler_(EISetExpelled.class, hdSetExpelled)
            .setHandler_(EIListExpelledObjects.class, hdListExpelledObjects)
            .setHandler_(EIPauseOrResumeSyncing.class, hdPauseOrResumeSyncing)
            .setHandler_(EIExportFile.class, hdExportFile)
            .setHandler_(EIRelocateRootAnchor.class, hdRelocateRootAnchor)
            .setHandler_(EIListRevChildren.class, hdListRevChildren)
            .setHandler_(EIListRevHistory.class, hdListRevHistory)
            .setHandler_(EIExportRevision.class, hdExportRevision)
            .setHandler_(EIHeartbeat.class, hdHeartbeat)
            .setHandler_(EIGetActivities.class, hdGetActivities)

            // status events
            .setHandler_(EIGetSyncStatus.class, hdGetSyncStatus)
            .setHandler_(EIGetStatusOverview.class, hdGetStatusOverview)

            // net events
            .setHandler_(EIPresence.class, hdPresence)
            .setHandler_(EITransportMetricsUpdated.class, hdTransportMetricsUpdated)
            .setHandler_(EISessionEnded.class, hdSessionEnded)
            .setHandler_(EIUnicastMessage.class, hdUnicastMessage)
            .setHandler_(EIMaxcastMessage.class, hdMaxcastMessage)
            .setHandler_(EIStreamBegun.class, hdStreamBegun)
            .setHandler_(EIChunk.class, hdChunk)
            .setHandler_(EIStreamAborted.class, hdStreamAborted)
            .setHandler_(EIPulseStopped.class, hdPulseStopped)

            // mobile events
            .setHandler_(EIDownloadPacket.class, hdDownloadPacket);
    }
}
