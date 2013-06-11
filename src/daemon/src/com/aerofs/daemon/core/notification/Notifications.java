/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.lib.Path;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBIndexingProgress;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;

import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.proto.RitualNotifications.PBNotification.Type.BAD_CREDENTIAL;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.CONFLICT_COUNT;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.INDEXING_PROGRESS;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.PATH_STATUS;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.PATH_STATUS_OUT_OF_DATE;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.ROOTS_CHANGED;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.SHARED_FOLDER_JOIN;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.SHARED_FOLDER_KICKOUT;
import static com.aerofs.proto.RitualNotifications.PBNotification.Type.SHARED_FOLDER_PENDING;

public abstract class Notifications
{
    private Notifications()
    {
        // private to prevent instantiation
    }

    public static PBNotification newPathStatusOutOfDateNotification()
    {
        return PBNotification
                .newBuilder()
                .setType(PATH_STATUS_OUT_OF_DATE)
                .build();
    }

    public static PBNotification newRootsChangedNotification()
    {
        return PBNotification
                .newBuilder()
                .setType(ROOTS_CHANGED)
                .build();
    }

    public static PBNotification newBadCredentialReceivedNotification()
    {
        return PBNotification
                .newBuilder()
                .setType(BAD_CREDENTIAL)
                .build();
    }

    public static PBNotification newSharedFolderPendingNotification()
    {
        return PBNotification
                .newBuilder()
                .setType(SHARED_FOLDER_PENDING)
                .build();
    }

    public static PBNotification newSharedFolderJoinNotification(Path path)
    {
        return PBNotification
                .newBuilder()
                .setType(SHARED_FOLDER_JOIN)
                .setPath(path.toPB())
                .build();
    }

    public static PBNotification newSharedFolderKickoutNotification(Path path)
    {
        return PBNotification
                .newBuilder()
                .setType(SHARED_FOLDER_KICKOUT)
                .setPath(path.toPB())
                .build();
    }

    public static PBNotification newIndexingProgressNotification(int numFiles, int numFolders)
    {
       return PBNotification
               .newBuilder()
               .setType(INDEXING_PROGRESS)
               .setIndexingProgress(
                       PBIndexingProgress
                               .newBuilder()
                               .setFiles(numFiles)
                               .setFolders(numFolders))
               .build();
    }

    public static PBNotification newPathStatusNotification(Map<Path, PBPathStatus> statuses)
    {
        PBPathStatusEvent.Builder pathStatusBuilder = PBPathStatusEvent.newBuilder();
        for (Entry<Path, PBPathStatus> e : statuses.entrySet()) {
            pathStatusBuilder.addPath(e.getKey().toPB());
            pathStatusBuilder.addStatus(e.getValue());
        }

        return PBNotification
                .newBuilder()
                .setType(PATH_STATUS)
                .setPathStatus(pathStatusBuilder)
                .build();
    }

    public static PBNotification newConflictCountNotification(int numConflicts)
    {
        return PBNotification
                .newBuilder()
                .setType(CONFLICT_COUNT)
                .setConflictCount(numConflicts)
                .build();
    }
}
