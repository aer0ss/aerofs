#import "AeroFinderSync.h"

@interface AeroFinderSync ()

@end

@implementation AeroFinderSync

+ (void)initMyFolderURL {
    NSString* rootAnchor = [[AeroClient instance] rootAnchor];
    if(rootAnchor) {
        NSLog(@"Info: initialized with root anchor: %@.", rootAnchor);
        [FIFinderSyncController defaultController].directoryURLs = [NSSet setWithObject:
            [NSURL fileURLWithPath:rootAnchor]];
    }
}


+ (void)refreshBadgeImageWithStatus:(Overlay)status :(NSURL*) url {
    [[FIFinderSyncController defaultController] setBadgeIdentifier:[@(status) stringValue] forURL:url];
}

- (instancetype)init {
    self = [super init];

    NSLog(@"%s launched from %@ ; compiled at %s", __PRETTY_FUNCTION__, [[NSBundle mainBundle] bundlePath], __TIME__);

    [self connect];
    [self initBadgeImages];
    return self;
}

- (void)connect {
    NSLog(@"Info: connecting to AeroFS using default socket path.");
    NSString* socketFile = [NSString stringWithFormat:@"%@/%@", NSHomeDirectory(), @"shellext.sock"];
    [[AeroClient instance] reconnect:socketFile];
}

- (void)initBadgeImages {
    NSDictionary* iconNames = @{
                                @(DOWNLOADING) : @"download",
                                @(UPLOADING)   : @"upload",
                                @(CONFLICT)    : @"conflict",
                                @(IN_SYNC)     : @"in_sync",
                                @(OUT_SYNC)    : @"out_of_sync"
                                };

    NSBundle* aerofsBundle = [NSBundle bundleForClass:[self class]];
    for (NSNumber* key in iconNames) {
        NSImage* icon = [[NSImage alloc] initWithContentsOfFile:
                         [aerofsBundle pathForResource:[iconNames objectForKey:key] ofType:@"icns"]];
        if (icon) {
            [[FIFinderSyncController defaultController] setBadgeImage:icon label:[key stringValue]
                                                   forBadgeIdentifier:[key stringValue]];
        }
    }
}

#pragma mark - Primary Finder Sync protocol methods

- (void)requestBadgeIdentifierForURL:(NSURL*) url {
    Overlay status = [[AeroClient instance] overlayForPath:[url.filePathURL path]];
    [AeroFinderSync refreshBadgeImageWithStatus:status :url];
}

#pragma mark - Menu and toolbar item support

- (NSString*) toolbarItemName {
    return @"AeroFS";
}

- (NSImage*) toolbarItemImage {
    return [[NSImage alloc] initWithContentsOfFile:
            [[NSBundle bundleForClass:[self class]] pathForResource:@"sidebar" ofType:@"icns"]];
}

// Produce a menu for the extension.
- (NSMenu*) menuForMenuKind:(FIMenuKind)whichMenu {
    NSMenu* menu = [[NSMenu alloc] initWithTitle:@""];

    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        menu = [AeroContextMenu subMenuForPath:
                    [((NSURL*) [items objectAtIndex:0]).filePathURL path]];
        if(whichMenu != FIMenuKindToolbarItemMenu) {
            for(NSMenuItem* item in [menu itemArray]) {
                [item setImage: [[NSImage alloc] initWithContentsOfFile:
                             [[NSBundle bundleForClass:[self class]] pathForResource:@"menu" ofType:@"icns"]]];
            }
        }
//    } else if ([[AeroClient instance] rootAnchor] && ![[AeroClient instance] isUnderRootAnchor:
//                [[[FIFinderSyncController defaultController] targetedURL].filePathURL path]]){
//        NSMenuItem* openRootAnchor = [menu addItemWithTitle:@"Open AeroFS Folder" action:@selector(openRootAnchor:) keyEquivalent:@""];
//        [openRootAnchor setTarget:self];
    } else {
        [menu addItemWithTitle:@"No Available Actions" action:nil keyEquivalent:@""];
        [[menu itemAtIndex:0] setEnabled:NO];
    }
    return menu;
}

- (IBAction)showShareFolderDialog:(id)sender {
    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        [[AeroClient instance] showShareFolderDialog:[((NSURL*) [items objectAtIndex:0]).filePathURL path]];
    }
}

- (IBAction)showSyncStatusDialog:(id)sender {
    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        [[AeroClient instance] showSyncStatusDialog:[((NSURL*) [items objectAtIndex:0]).filePathURL path]];
    }
}

- (IBAction)showVersionHistoryDialog:(id)sender {
    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        [[AeroClient instance] showVersionHistoryDialog:[((NSURL*) [items objectAtIndex:0]).filePathURL path]];
    }
}

- (IBAction)showConflictResolutionDialog:(id)sender {
    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        [[AeroClient instance] showConflictResolutionDialog:[((NSURL*) [items objectAtIndex:0]).filePathURL path]];
    }
}

- (IBAction)createLink:(id)sender {
    NSArray* items = [[FIFinderSyncController defaultController] selectedItemURLs];
    if([items count] == 1) {
        [[AeroClient instance] createLink:[((NSURL*) [items objectAtIndex:0]).filePathURL path]];
    }
}

- (IBAction)openRootAnchor:(id)sender {
//    NSLog(@"Info: openRootAnchor1: %@.", [[AeroClient instance] rootAnchor]);
//    BOOL result1 =[[NSWorkspace sharedWorkspace] openFile:[[AeroClient instance] rootAnchor]];
//    NSLog((result1) ? @"result1: True" : @"result1: False");
//
//    NSLog(@"Info: openRootAnchor2: %@.", [[AeroClient instance] rootAnchor]);
//    BOOL result2 = [[NSWorkspace sharedWorkspace] openURL:[NSURL fileURLWithPath:[[AeroClient instance] rootAnchor]]];
//    NSLog((result2) ? @"result2: True" : @"result2: False");
//
//    NSLog(@"Info: openRootAnchor3: %@.", [[AeroClient instance] rootAnchor]);
//    BOOL result3 = [[NSWorkspace sharedWorkspace] selectFile:nil inFileViewerRootedAtPath:[[AeroClient instance] rootAnchor]];
//    NSLog((result3) ? @"result3: True" : @"result3: False");
}

@end