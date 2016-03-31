#import "AeroClient.h"
#import "AeroContextMenu.h"
#import "AeroSocket.h"
#import "AeroOverlay.h"
#import "../../../osx_common/gen/Shellext.pb.h"

#import  "common.h"
#import "AeroFinderSync.h"

// The part of file path after the HOME dir.
NSString* const DEFAULT_RTROOT = @"~/Library/Application Support/AeroFS";

NSString* const DEFAULT_SOCKET_FILE_NAME = @"shellext.sock";

static Overlay overlayForStatus(PBPathStatus* status)
{
    // temporary downloading takes precedence over potentially long-lasting states
    if (status.flags & PBPathStatus_FlagDownloading)    return DOWNLOADING;
    // conflict state takes precedence over sync status
    if (status.flags & PBPathStatus_FlagConflict)       return CONFLICT;
    // in-sync takes precendence over uploading
    if (status.sync == PBPathStatus_SyncInSync)         return IN_SYNC;
    // uploading takes precedence over out-of-sync
    if (status.flags & PBPathStatus_FlagUploading)      return UPLOADING;
    if (status.sync == PBPathStatus_SyncOutSync)        return OUT_SYNC;
    return NONE;
}

@interface AeroClient (Private)
- (void)setRootAnchor:(NSString*) path;
- (void)setUserId:(NSString*)user;
- (void)onWakeFromSleep:(NSNotification*)notification;
- (void)clearCache;
- (void)evicted:(NSString*)path withValue:(int)value;
- (void)refreshIconInFinder:(NSString*)path;
- (void)scheduleRefreshAllFinderWindows;
- (void)refreshAllFinderWindows;
@end

@implementation AeroClient

@synthesize overlay;
@synthesize contextMenu;
@synthesize isLinkSharingEnabled;
@synthesize rootAnchor;

/**
 * Returns the shared instance of AeroClient
 */
+(AeroClient*) instance
{
    static AeroClient* inst = nil;
    if (inst == nil) {
        inst = [[AeroClient alloc] init];
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

    statusCache = [[AeroOverlayCache alloc] initWithLimit:100000];
    [statusCache setDelegate:self];

    [[[NSWorkspace sharedWorkspace] notificationCenter] addObserver: self
               selector: @selector(onWakeFromSleep:)
               name: NSWorkspaceDidWakeNotification object: NULL];

    return self;
}

- (void)reconnect:(NSString*)sockFile
{
    NSLog(@"AeroFS: Trying to connect to the server on sock file: %@...", sockFile);
    [self clearCache];
    [socket connectToServerOnSocket:sockFile];
}

- (void)disconnect
{
    NSLog(@"AeroFS: Disconnecting from the server");
    [self clearCache];
    [socket disconnect];
}

- (BOOL)isConnected
{
    NSLog(@"AeroFS: Checking if connected to the server");
    return [socket isConnected];
}

/**
 * Implementation of the "Create Link" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)createLink:(NSString*)path
{
    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeCreateLink;
    builder.createLink = [[[CreateLinkCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Share Folder" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showShareFolderDialog:(NSString*)path
{
    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeShareFolder;
    builder.shareFolder = [[[ShareFolderCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Sync status" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showSyncStatusDialog:(NSString*)path
{
    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeSyncStatus;
    builder.syncStatus = [[[SyncStatusCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Sync History" context menu item
 * The sender must set its represented object to the path of the folder
 */
- (void)showVersionHistoryDialog:(NSString*)path
{
    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeVersionHistory;
    builder.versionHistory = [[[VersionHistoryCall builder] setPath:path] build];

    [socket sendMessage: builder.build];
}

/**
 * Implementation of the "Resolve conflict" context menu item
 * The sender must set its represented object to the path of the file
 */
- (void)showConflictResolutionDialog:(NSString*)path
{
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
    [AeroFinderSync initMyFolderURL];
}

-(NSImage*)iconForPath:(NSString*)path
{
    return [overlay iconForPath:path];
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
    return NO;//[userId hasSuffix:@"@aerofs.com"];
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
    NSAssert(notification.path.length > 0, @"AeroClient: received notification with empty path");

    if (![self isUnderRootAnchor:notification.path]) {
        NSLog(@"Received status update for path outside root anchor: %@", notification.path);
        return;
    }

    PBPathStatus* status = notification.status;

    Overlay o = overlayForStatus(status);
    // To preserve locality, discard notifications for any path not yet requested by Finder
    if ([statusCache overlayForPath:notification.path] != -1) {
        [statusCache setOverlay:o forPath:notification.path];
        // NSLog(@"AeroFS: overlay update %@ %d", notification.path, status.sync);
        [self refreshIconInFinder:notification.path];
    } else {
        // NSLog(@"AeroFS: overlay discard %@ %d", notification.path, status.sync);
    }
}

/**
 * Get Finder to refresh icon overlay for a given path
 */
- (void)refreshIconInFinder:(NSString*)path
{
    if(path) {
        [AeroFinderSync refreshBadgeImageWithStatus:[self overlayForPath:path]
            :[NSURL fileURLWithPath:path]];
    }
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
