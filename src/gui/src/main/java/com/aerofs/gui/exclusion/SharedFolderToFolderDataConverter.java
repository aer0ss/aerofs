/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.exclusion;

import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SharedFolderToFolderDataConverter
{
    private static Map<Path, FolderData> getTopLevelInternalFolders() throws Exception
    {
        Map<Path, FolderData> internalFolders = Maps.newHashMap();
        Path root = Path.root(Cfg.rootSID());
        // This will get all the top level children inside the AeroFS folder. This includes
        // shared and unshared folders.
        GetChildrenAttributesReply reply = UIGlobals.ritual().getChildrenAttributes(root.toPB());

        for (int i = 0; i < reply.getChildrenNameCount(); i++) {
            if (reply.getChildrenAttributes(i).getType() != Type.FILE) {
                // Get folder data information.
                String name = reply.getChildrenName(i);
                Path path = root.append(name);
                String absPath = UIUtil.absPathNullable(path);
                FolderData folderData = new FolderData(name, false, true, absPath);
                internalFolders.put(path, folderData);
            }
        }
        return internalFolders;
    }

    /**
     * This function resolves the set of all PBSharedFolders to folder data.
     * We only include those folders that are external shared folders or are top level internal
     * shared folders. This is because we don't display internal shared folders that are deeper
     * than the top level in the Selective Sync dialog. For example: if AeroFS/foo is shared,
     * we display foo but if AeroFS/foo/fooInternal is shared we don't display fooInternal.
     */
    static Map<Path, FolderData> resolveStoresToPathFolderDataMap(Collection<PBSharedFolder> stores)
    {
        Map<Path, FolderData> pathToFolderData =  Maps.newLinkedHashMap();

        for (PBSharedFolder sharedFolder: stores) {
            Path path = Path.fromPB(sharedFolder.getPath());
            if (path.toPB().getElemCount() <= 1) {
                FolderData folderData = new FolderData(sharedFolder.getName(),
                        true, path.toPB().getElemCount() == 1, UIUtil.absPathNullable(path));
                pathToFolderData.put(path, folderData);
            }
        }
        return pathToFolderData;
    }

    /**
     * This function returns the set of all internal folders which include:
     * 1. All top level internal shared folders
     * 2. All top level internal non-shared folders.
     */
    static Map<Path, FolderData> getAllInternalFolders(Collection<PBSharedFolder> stores)
            throws Exception{
        Map<Path, FolderData> topLevelInternalFolders = getTopLevelInternalFolders();
        Map<Path, FolderData> internalStores = resolveStoresToPathFolderDataMap(stores);

        // The <Path, Path, FolderData> forces the typed parameters explicitly. This is needed
        // because of an OpenJDK6 type inference More details found here:
        // https://code.google.com/p/guava-libraries/issues/detail?id=635 and
        // https://bugs.openjdk.java.net/browse/JDK-6569074
        Map<Path, FolderData> allInternal =
                Maps.<Path, Path, FolderData>newTreeMap(new Comparator<Path>()
        {
            @Override
            public int compare(Path p1, Path p2)
            {
                return p1.last().compareTo(p2.last());
            }
        });

        allInternal.putAll(topLevelInternalFolders);
        allInternal.putAll(internalStores);
        return allInternal;
    }

    /**
     * This function returns a set of excluded folders by querying:
     * 1. ListExcludedFolders which provided list of internal folders that have been excluded. This
     * also included non-shared internal folders.
     * 2. Obtaining list of not admitted nor linked folders from ListSharedFolders ritual call.
     * There is an overlap of unadmitted internal shared folders but that's okay since we return
     * a set.
     */
    static Set<Path> getAllExcludedFolders(List<PBSharedFolder> sharedFolders)
            throws Exception {
        Set<Path> excludedFolders = Sets.newHashSet();

        for(PBPath pbPath: UIGlobals.ritual().listExcludedFolders().getPathList()) {
            excludedFolders.add(Path.fromPB(pbPath));
        }

        for (PBSharedFolder sharedFolder: sharedFolders) {
            if (!sharedFolder.getAdmittedOrLinked()) {
                excludedFolders.add(Path.fromPB(sharedFolder.getPath()));
            }
        }
        return excludedFolders;
    }
}
