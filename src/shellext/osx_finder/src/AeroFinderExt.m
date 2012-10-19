#import <Quartz/Quartz.h>
#import "AeroFinderExt.h"
#import "AeroContextMenu.h"
#import "AeroSocket.h"
#import "AeroOverlay.h"
#import "AppleEventConstants.h"
#import "../gen/Shellext.pb.h"

#define GUIPORT_DEFAULT 50195
#define PROTOCOL_VERSION 4

// use static NSNumber instances to reduce memory usage
static NSNumber** _cacheValues = nil;

static void initCacheValues()
{
    if (_cacheValues == nil) {
        _cacheValues = malloc(OverlayCount * sizeof(NSNumber*));
        for (int i = 0; i < OverlayCount; ++i) {
            _cacheValues[i] = [NSNumber numberWithInt:i];
        }
    }
}

static Overlay overlayForStatus(PBPathStatus* status)
{
    if (status.flags & PBPathStatus_FlagDownloading)   return DOWNLOADING;
    if (status.flags & PBPathStatus_FlagUploading)     return UPLOADING;
    switch (status.sync) {
        case PBPathStatus_SyncInSync:               return IN_SYNC;
        case PBPathStatus_SyncPartialSync:          return PARTIAL_SYNC;
        case PBPathStatus_SyncOutSync:              return OUT_SYNC;
        default: break;
    }
    return NONE;
}

@interface AeroFinderExt (Private)
- (void)setRootAnchor:(NSString*) path;
- (void)setUserId:(NSString*)user;
- (void)onWakeFromSleep:(NSNotification*)notification;
@end

@implementation AeroFinderExt

@synthesize overlay;
@synthesize contextMenu;

- (void) dealloc
{
    [socket release];
    [overlay release];
    [contextMenu release];
    [statusCache release];
    [rootAnchor release];
    [userId release];
    [super dealloc];
}

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
    int portNumber = [[desc descriptorForKeyword:PORT_KEYWORD] int32Value];
    [desc release];

    if (portNumber <=0) {
        NSLog(@"Warning: AeroFS didn't specify which port it's listening to, using default.");
        portNumber = GUIPORT_DEFAULT;
    }

    [[AeroFinderExt instance] reconnect:portNumber];

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
    NSLog(@"AeroFS: Loading Finder Extension...");

    self = [super init];
    if (!self) {
        return nil;
    }

    socket = [[AeroSocket alloc] init];
    overlay = [[AeroOverlay alloc] init];
    contextMenu = [[AeroContextMenu alloc] init];

    initCacheValues();
    syncStatCached = NO;
    statusCache = [[NSCache alloc] init];
    [statusCache setCountLimit:100000];

    [[[NSWorkspace sharedWorkspace] notificationCenter] addObserver: self
               selector: @selector(onWakeFromSleep:)
               name: NSWorkspaceDidWakeNotification object: NULL];

    return self;
}

-(void) reconnect:(UInt16)port
{
    NSLog(@"AeroFS: Trying to connect to the server on port %u...", port);
    [self clearCache];
    [socket connectToServerOnPort:port];
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
 * Implementation of the "Version History" context menu item
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

- (void)sendGreeting
{
    ShellextCall* call = [[[[ShellextCall builder]
            setType:ShellextCall_TypeGreeting]
            setGreeting:[[[GreetingCall builder] setProtocolVersion:PROTOCOL_VERSION] build]]
            build];

    [socket sendMessage:call];
}

- (Overlay)overlayForPath:(NSString*)path
{
    Overlay status = NONE;
    NSNumber* val = [statusCache objectForKey:path];
    if (val == nil) {
        // put placeholder in cache to avoid sending multiple requests to GUI
        [statusCache setObject:_cacheValues[NONE] forKey:path];

        // cache miss: fetch from GUI for next time...
        ShellextCall* call = [[[[ShellextCall builder]
                setType:ShellextCall_TypeGetPathStatus]
                setGetPathStatus:[[[GetPathStatusCall builder] setPath:path] build]] build];

        [socket sendMessage:call];
        // TODO: direct communication to daemon to reduce latency?
    } else {
        // cache hit: convert integer to enum
        status = (Overlay)[val intValue];
    }
    if (![self shouldEnableTestingFeatures]) {
        if (status != UPLOADING && status != DOWNLOADING) return NONE;
    }
    return status;
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
    NSFileManager* fileManager = [[[NSFileManager alloc] init] autorelease];
    [fileManager fileExistsAtPath:path isDirectory:&isDir];
    if (isDir) {
        flags |= Directory;
    }
    return flags;
}

-(void) setRootAnchor:(NSString*)path
{
    NSAssert(path.length > 0, @"setRootAnchorPath: path can't be empty or nil");
    NSAssert(![path hasSuffix:@"/"], @"setRootAnchorPath: anchor root can't have trailing slash");
    NSLog(@"AeroFS: Finder Extension initialized with root anchor: %@", path);

    [rootAnchor autorelease];
    rootAnchor = [path copy];
    [self clearCache];
}

-(void) setUserId:(NSString*)user
{
    [userId autorelease];
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

        case ShellextNotification_TypeClearStatusCache:
            [statusCache removeAllObjects];
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

    if (status.sync == PBPathStatus_SyncUnknown) {
        // sync status no longer reliable: need to clear cache
        // use an extra boolean flag to avoid repeatedly clearing the cache when it does not contain
        // sync status information
        if (syncStatCached) {
            [self clearCache];
        }
    } else {
        syncStatCached = YES;
    }

    Overlay o = overlayForStatus(status);
    if (o != NONE) {
        // To preserve locality, discard notifications for any path not yet requested by Finder
        if ([statusCache objectForKey:notification.path] != nil) {
            [statusCache setObject:_cacheValues[o] forKey:notification.path];
        }
    }

    //NSLog(@"Received status update %@ %d:%d %d", notification.path, status.sync, status.flags, o);
}

-(void)clearCache
{
    syncStatCached = NO;
    [statusCache removeAllObjects];
}

- (void)onWakeFromSleep:(NSNotification*)notification
{
    [self clearCache];
}

@end
