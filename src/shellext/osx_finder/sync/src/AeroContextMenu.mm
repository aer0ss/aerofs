#import <objc/runtime.h>

#import "AeroContextMenu.h"
#import "AeroClient.h"

@implementation AeroContextMenu

-(id)init
{
    self = [super init];
    if (!self) {
        return nil;
    }

    return self;
}

+(NSMenu*)subMenuForPath:(NSString*)path
{
    NSMenu* subMenu = [[NSMenu alloc] init];

    if (![[AeroClient instance] shouldModifyFinder] || path == nil || ![[AeroClient instance] isUnderRootAnchor:path]) {
        [subMenu addItemWithTitle:@"No Available Actions" action:nil keyEquivalent:@""];
        [[subMenu itemAtIndex:0] setEnabled:NO];
        return subMenu;
    }

    int flags = [[AeroClient instance] flagsForPath:path];

    int idx = 0;

    if ((flags & Directory) && !(flags & RootAnchor)) {
        NSMenuItem* share = [subMenu insertItemWithTitle:NSLocalizedString(@"Share This Folder...", @"Context menu")
                                                  action:@selector(showShareFolderDialog:)
                                           keyEquivalent:@""
                                                 atIndex:idx];

        [share setTarget: [AeroClient instance]];
        [share setRepresentedObject:path];
        ++idx;
    }
    
    if (((flags & (Directory | File)) && !(flags & RootAnchor))
            && [[AeroClient instance] isLinkSharingEnabled]) {
        NSMenuItem* create_link = [subMenu insertItemWithTitle:NSLocalizedString(@"Create AeroFS Link...", @"Context menu")
                                                  action:@selector(createLink:)
                                           keyEquivalent:@""
                                                 atIndex:idx];

        [create_link setTarget: [AeroClient instance]];
        [create_link setRepresentedObject:path];
        ++idx;
    }

    if (!(flags & Directory) && [[AeroClient instance] overlayForPath:path] == CONFLICT) {
        NSMenuItem* conflict = [subMenu insertItemWithTitle:NSLocalizedString(@"Resolve Conflict...", @"Context menu")
                                                     action:@selector(showConflictResolutionDialog:)
                                              keyEquivalent:@""
                                                    atIndex:idx];
        [conflict setTarget: [AeroClient instance]];
        [conflict setRepresentedObject:path];
        ++idx;
    }

    if ([[AeroClient instance] shouldEnableTestingFeatures]) {
        NSMenuItem* history = [subMenu insertItemWithTitle:NSLocalizedString(@"Sync History...", @"Context menu")
                                                    action:@selector(showVersionHistoryDialog:)
                                             keyEquivalent:@""
                                                   atIndex:idx];
        [history setTarget: [AeroClient instance]];
        [history setRepresentedObject:path];
        ++idx;
        
        NSMenuItem* syncstat = [subMenu insertItemWithTitle:NSLocalizedString(@"Sync Status...", @"Context menu")
                                                     action:@selector(showSyncStatusDialog:)
                                              keyEquivalent:@""
                                                    atIndex:idx];
        [syncstat setTarget: [AeroClient instance]];
        [syncstat setRepresentedObject:path];
        ++idx;
    }

    return subMenu;
}

@end
