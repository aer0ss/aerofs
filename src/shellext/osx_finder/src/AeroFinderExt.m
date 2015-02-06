#import <Quartz/Quartz.h>
#import "AeroFinderExt.h"
#import "AeroContextMenu.h"
#import "AeroSocket.h"
#import "AeroOverlay.h"
#import "AppleEventConstants.h"
#import "../gen/Shellext.pb.h"
#import "AeroOverlayCache.h"

#import  "../../common/common.h"
#import "AeroSidebarIcon.h"

// The part of file path after the HOME dir.
NSString * const DEFAULT_RTROOT = @"Library/Application Support/AeroFS";

NSString * const DEFAULT_SOCKET_FILE_NAME = @"shellext.sock";

static Overlay overlayForStatus(PBPathStatus* status)
{
    // temporary states (Upload/Download) take precedence over potentially long-lasting ones
    if (status.flags & PBPathStatus_FlagDownloading)    return DOWNLOADING;
    if (status.flags & PBPathStatus_FlagUploading)      return UPLOADING;
    // conflict state takes precedence over sync status
    if (status.flags & PBPathStatus_FlagConflict)       return CONFLICT;
    switch (status.sync) {
        case PBPathStatus_SyncInSync:                   return IN_SYNC;
        case PBPathStatus_SyncPartialSync:              return IN_SYNC;
        case PBPathStatus_SyncOutSync:                  return OUT_SYNC;
        default: break;
    }
    return NONE;
}

@interface AeroFinderExt (Private)
- (void)setRootAnchor:(NSString*) path;
- (void)setUserId:(NSString*)user;
- (void)onWakeFromSleep:(NSNotification*)notification;
- (void)clearCache;
- (void)evicted:(NSString*)path withValue:(int)value;
- (void)refreshIconInFinder:(NSString*)path;
- (void)scheduleRefreshAllFinderWindows;
- (void)refreshAllFinderWindows;
@end

@implementation AeroFinderExt

@synthesize overlay;
@synthesize contextMenu;
@synthesize sidebarIcon;
@synthesize isLinkSharingEnabled;


/**
 * This is the handler of our "fake" Apple Script event.
 * Upon reception of the "aeroload" command from our finder_inject executable, OS X will do the following steps:
 * 1. Look for all *.osax bundles in /Library/ScriptingAdditions
 * 2. Read their Info.plist and find the name of the commands that they implement
 * 3. Find out that we implement aeroload and that this function is the handler
 * 4. Inject our code into the Finder and call this function
 *
 * Note: the name of this function must match the name declared in the Info.plist file
 */
OSErr AeroLoadHandler(const AppleEvent* event, AppleEvent* reply, long refcon)
{
    //TODO: Use [NSBundle mainBundle] to get info about the executable we're being loaded into, and check if it's the right Finder version

    NSAppleEventDescriptor* desc = [[NSAppleEventDescriptor alloc] initWithAEDescNoCopy: event];
    NSString *socketFile = [[desc descriptorForKeyword: SOCK] stringValue];
    if (socketFile == nil) {
        NSLog(@"Warning: AeroFS didn't specify which native socket file it's listening to, using default.");
        socketFile = [NSString stringWithFormat:@"%@/%@/%@", NSHomeDirectory(), DEFAULT_RTROOT, DEFAULT_SOCKET_FILE_NAME];
    }

    [[AeroFinderExt instance] reconnect:socketFile];

    return noErr;
}

/**
 * Returns the shared instance of AeroFinderExt
 */
+(AeroFinderExt*) instance
{
    static AeroFinderExt* inst = nil;
    if (inst == nil) {
        inst = [[AeroFinderExt alloc] init];
    }
    return inst;
}

-(id) init
{
    NSBundle* bundle = [NSBundle bundleForClass:[self class]];
    NSString* version = [[bundle infoDictionary] objectForKey:(NSString*)kCFBundleVersionKey];
    NSLog(@"AeroFS: Loading Finder Extension v%@...", version);

    self = [super init];
    if (!self) {
        return nil;
    }

    socket = [[AeroSocket alloc] init];
    overlay = [[AeroOverlay alloc] init];
    contextMenu = [[AeroContextMenu alloc] init];
    sidebarIcon = [[AeroSidebarIcon alloc] init];

    statusCache = [[AeroOverlayCache alloc] initWithLimit:100000];
    [statusCache setDelegate:self];

    [[[NSWorkspace sharedWorkspace] notificationCenter] addObserver: self
               selector: @selector(onWakeFromSleep:)
               name: NSWorkspaceDidWakeNotification object: NULL];

    return self;
}

-(void) reconnect:(NSString*)sockFile
{
    NSLog(@"AeroFS: Trying to connect to the server on sock file: %@...", sockFile);
    [self clearCache];
    [socket connectToServerOnSocket:sockFile];
}

/**
 * Implementation of the "Create Link" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)createLink:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeCreateLink;
    builder.createLink = [[[CreateLinkCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Share Folder" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showShareFolderDialog:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeShareFolder;
    builder.shareFolder = [[[ShareFolderCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Sync status" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showSyncStatusDialog:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeSyncStatus;
    builder.syncStatus = [[[SyncStatusCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Sync History" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showVersionHistoryDialog:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeVersionHistory;
    builder.versionHistory = [[[VersionHistoryCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Resolve conflict" context menu item
 * The sender must set its represented object to the path of the file
 */
- (void)showConflictResolutionDialog:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeConflictResolution;
    builder.conflictResolution = [[[ConflictResolutionCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

- (void)sendGreeting
{
    ShellextCall* call = [[[[ShellextCall builder]
            setType:ShellextCall_TypeGreeting]
            setGreeting:[[[GreetingCall builder] setProtocolVersion:PROTOCOL_VERSION] build]]
            build];

    [socket sendMessage:call];
}

/**
 * Lookup overlay in the cache and pull from the GUI on miss
 */
- (Overlay)overlayForPath:(NSString*)path
{
    Overlay status = NONE;
    int val = [statusCache overlayForPath:path];
    if (val == -1) {
        //NSLog(@"AeroFS: overlay miss %@", path);
        // put placeholder in cache to avoid sending multiple requests to GUI
        [statusCache setOverlay:NONE forPath:path];

        // cache miss: fetch from GUI for next time...
        ShellextCall* call = [[[[ShellextCall builder]
                setType:ShellextCall_TypeGetPathStatus]
                setGetPathStatus:[[[GetPathStatusCall builder] setPath:path] build]] build];

        [socket sendMessage:call];
        // TODO: direct communication to daemon to reduce latency?
    } else {
        // TODO: clear placeholders after a cooldown period?
        // cache hit: convert integer to enum
        status = (Overlay)val;
    }
    if (![self shouldEnableTestingFeatures]) {
        if (status != UPLOADING && status != DOWNLOADING && status != CONFLICT) return NONE;
    }
    return status;
}

/**
 * Called on eviction of a value from the cache
 */
- (void)evicted:(NSString*)path withValue:(int)value
{
    //NSLog(@"AeroFS: overlay eviction %@ %d", path, value);
    // if the path is still visible, make sure it gets refreshed asap...
    [self refreshIconInFinder:path];
}

- (BOOL)isUnderRootAnchor:(NSString*)path
{
    if (path.length == 0 || rootAnchor.length == 0) {
        return NO;
    }
    return [path hasPrefix:rootAnchor] && (path.length == rootAnchor.length || [path characterAtIndex:rootAnchor.length] == '/');
}

/**
* Compute path flags for a given path (mostly used to determine how to alter the Finder context menu)
* The flags are an OR combination of values defined in the PathFlag enum
*/
- (int)flagsForPath:(NSString*)path
{
    int flags = 0;

    if ([path isEqualToString:rootAnchor]) {
        flags |= RootAnchor;
    }

    BOOL isDir = NO;
    if ([[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&isDir]) {
        if (isDir) {
            flags |= Directory;
        } else {
            flags |= File;
        }
    }

    return flags;
}

-(void) setRootAnchor:(NSString*)path
{
    NSAssert(path.length > 0, @"setRootAnchorPath: path can't be empty or nil");
    NSAssert(![path hasSuffix:@"/"], @"setRootAnchorPath: anchor root can't have trailing slash");
    NSLog(@"AeroFS: Finder Extension initialized with root anchor: %@", path);

    rootAnchor = [path copy];
    [self clearCache];
}

-(void) setUserId:(NSString*)user
{
    userId = [user copy];
}

/**
 Returns YES if we are connected to the AeroFS GUI.
 If the user quits AeroFS, or if we are no longer connected, this function will return NO
 We call this before doing any modification on the Finder.
 */
- (BOOL)shouldModifyFinder
{
    return [socket isConnected] && (rootAnchor.length > 0);
}

/**
* Return YES if the root anchor we are operating on belongs to an @aerofs.com user
*/
- (BOOL)shouldEnableTestingFeatures
{
    if (userId == NULL || userId.length == 0) {
        return NO;
    }
    return [userId hasSuffix:@"@aerofs.com"];
}

-(void) parseNotification:(ShellextNotification*)notification
{
    switch ([notification type]) {
        case ShellextNotification_TypePathStatus:
            [self onStatus:notification.pathStatus];
            break;

        case ShellextNotification_TypeRootAnchor:
            [self setRootAnchor:notification.rootAnchor.path];
            if (notification.rootAnchor.hasUser) {
                [self setUserId:notification.rootAnchor.user];
            }
            break;

        case ShellextNotification_TypeLinkSharingEnabled:
            isLinkSharingEnabled = notification.linkSharingEnabled.isLinkSharingEnabled;
            break;

        case ShellextNotification_TypeClearStatusCache:
            [self clearCache];
            break;

        default:
            break;
    }
}

-(void) onStatus:(PathStatusNotification*)notification
{
    NSAssert(notification.path.length > 0, @"AeroFinderExt: received notification with empty path");

    if (![self isUnderRootAnchor:notification.path]) {
        NSLog(@"Received status update for path outside root anchor: %@", notification.path);
        return;
    }

    PBPathStatus* status = notification.status;

    Overlay o = overlayForStatus(status);
    // To preserve locality, discard notifications for any path not yet requested by Finder
    if ([statusCache overlayForPath:notification.path] != -1) {
        [statusCache setOverlay:o forPath:notification.path];
        //NSLog(@"AeroFS: overlay update %@ %d", notification.path, status.sync);
        [self refreshIconInFinder:notification.path];
    } else {
        //NSLog(@"AeroFS: overlay discard %@ %d", notification.path, status.sync);
    }
}

/**
 * Get Finder to refresh icon overlay for a given path
 */
- (void)refreshIconInFinder:(NSString*)path
{
    // attempt 1: [[NSWorkspace sharedWorkspace] noteFileSystemChanged:path]

    // attempt 2: AppleScript / Apple Events (tell application "Finder" to update ...) neither of
    // which seem to make any difference.

    // attempt 3: aggressive display refresh of all windows. works great, only concern is slightly
    // degraded performance of Finder due to burst of refreshes.
    [self scheduleRefreshAllFinderWindows];

    // attempt 4: dirty hack in the bowels of Finder?
}

/**
 * Too avoiding degradation of Finder performance due to aggressive refreshes when receiving a flood
 * of status updates we use NSTimer to schedule the refresh in the future and implement
 * rate-limiting by dropping incoming refresh requests until a timer exists (they are effectively
 * "merged" when the timer fires and causes a refresh).
 */
- (void)scheduleRefreshAllFinderWindows
{
    // Incoming refresh, no worries...
    if (refreshTimer != nil) return;

    NSTimeInterval now = [NSDate timeIntervalSinceReferenceDate];
    NSTimeInterval elapsed = now - lastRefreshTime;

    // More than 1s elapsed since last refresh, can afford direct refresh.
    if (elapsed > 1) {
        [self refreshAllFinderWindows];
        return;
    }

    // Single shot timer with 500ms delay (docs says to expect a resolution no finer than
    // ~50-100ms).
    refreshTimer = [NSTimer scheduledTimerWithTimeInterval:0.5
                                                    target:self
                                                  selector:@selector(refreshAllFinderWindows)
                                                  userInfo:nil
                                                   repeats:NO];
}


/**
 * Get Finder to refresh all icon overlays (if any) [without enumerating cache contents...]
 */
- (void)refreshAllFinderWindows
{
    [[[NSApplication sharedApplication] windows] enumerateObjectsUsingBlock:^(id w, NSUInteger i, BOOL* b) {
        [w display];
    }];

    lastRefreshTime = [NSDate timeIntervalSinceReferenceDate];
    refreshTimer = nil;
}

/**
 * Clear any cached data and refresh Finder windows
 */
-(void)clearCache
{
    //NSLog(@"AeroFS: overlay clear");
    [statusCache clear];

    // make sure Finder discards all our icon overlays, the aggressive way
    [self refreshAllFinderWindows];
}

- (void)onWakeFromSleep:(NSNotification*)notification
{
    [self clearCache];
}

@end
