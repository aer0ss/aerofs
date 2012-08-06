#import "AeroFinderExt.h"
#import "AeroContextMenu.h"
#import "AeroSocket.h"
#import "AeroOverlay.h"
#import "AeroNode.h"
#import "AppleEventConstants.h"
#import "../gen/Shellext.pb.h"

#define GUIPORT_DEFAULT 50195
#define PROTOCOL_VERSION 1

@interface AeroFinderExt (Private)
- (void)setRootAnchor:(NSString*) path;
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
    [rootAnchor release];
    [super dealloc];
}

/**
 This is the handler of our "fake" Apple Script event.
 Upon reception of the "aeroload" command from our finder_inject executable, OS X will do the following steps:
 1. Look for all *.osax bundles in /Library/ScriptingAdditions
 2. Read their Info.plist and find the name of the commands that they implement
 3. Find out that we implement aeroload and that this function is the handler
 4. Inject our code into the Finder and call this function

 Note: the name of this function must match the name declared in the Info.plist file
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
 Returns the shared instance of AeroFinderExt
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

    [[[NSWorkspace sharedWorkspace] notificationCenter] addObserver: self
               selector: @selector(onWakeFromSleep:)
               name: NSWorkspaceDidWakeNotification object: NULL];

    return self;
}

-(void) reconnect:(UInt16)port
{
    NSLog(@"AeroFS: Trying to connect to the server on port %u...", port);
    [overlay clearCache:rootAnchor];
    [socket connectToServerOnPort:port];
}

/**
 Implementation of the "Share Folder" context menu item
 The sender must set its represented object to the path of the folder
 */
- (void)showShareFolderDialog:(id)sender
{
    NSString* path = [sender representedObject];

    ShellextCall_Builder* builder = [ShellextCall builder];
    builder.type = ShellextCall_TypeShareFolder;
    builder.shareFolder = [[[ShareFolderCall builder] setPath:path] build];

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
* Returns whether we should display an AeroFS context menu entry for a given path
* Returns YES if and only if:
*   - path is under root anchor
*   - path is a folder
*/
- (BOOL)shouldDisplayContextMenu:(NSString*)path
{
    if (path.length == 0 || rootAnchor.length == 0) {
        return NO;
    }

    // Check that path is under root anchor and is a folder
    if ([path hasPrefix:rootAnchor] && path.length > rootAnchor.length) {
        NSFileManager* fileManager = [[[NSFileManager alloc] init] autorelease];
        BOOL isDir = false;
        [fileManager fileExistsAtPath:path isDirectory:&isDir];
        if (isDir) {
            return YES;
        }
    }

    return NO;
}

-(void) setRootAnchor:(NSString*)path
{
    NSAssert(path.length > 0, @"setRootAnchorPath: path can't be empty or nil");
    NSAssert(![path hasSuffix:@"/"], @"setRootAnchorPath: anchor root can't have trailing slash");
    NSLog(@"AeroFS: Finder Extension initialized with root anchor: %@", path);

    [rootAnchor autorelease];
    rootAnchor = [path copy];
    [overlay clearCache:rootAnchor];
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

-(void) parseNotification:(ShellextNotification*)notification
{
    switch ([notification type]) {
        case ShellextNotification_TypeFileStatus:
            [self onFileStatus:notification.fileStatus];
            break;

        case ShellextNotification_TypeRootAnchor:
            [self setRootAnchor:notification.rootAnchor.path];
            break;

        case ShellextNotification_TypeClearStatusCache:
            [overlay clearCache:rootAnchor];
            break;

        default:
            break;
    }
}

-(void) onFileStatus:(FileStatusNotification*)notification
{
    NSAssert(notification.path.length > 0, @"AeroFinderExt: received FileStatusReply with empty path");

    AeroNode* node = [overlay.rootNode getNodeAtPath:notification.path createPath:YES];

    if (!node) {
        NSLog(@"AeroFS ERROR: Received notification for a file outside root: %@", notification.path);
    }

    // Converts the statuses from the PBFileStatusReply to a NodeStatus

    NodeStatus st = node.status;
    if (notification.hasDownloading) {
        st = notification.downloading ? st | Downloading : st & ~Downloading;
    }
    if (notification.hasUploading) {
        st = notification.uploading ? st | Uploading : st & ~Uploading;
    }
    node.status = st;
}

- (void)onWakeFromSleep:(NSNotification*)notification
{
    [overlay clearCache:rootAnchor];
}

@end
