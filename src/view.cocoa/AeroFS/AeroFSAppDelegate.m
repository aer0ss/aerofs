#import "AeroFSAppDelegate.h"
#import "AeroController.h"
#import "AeroSetupDialog.h"
#import "AeroMenu.h"

@interface AeroFSAppDelegate(Private)
- (void)onSetupStatusReceived:(GetInitialStatusReply*)reply error:(NSError*)error;
@end

@implementation AeroFSAppDelegate

@synthesize trayMenu;

- (void)dealloc
{
    [super dealloc];
}

- (void)applicationDidFinishLaunching:(NSNotification *)aNotification
{
    [trayMenu showInTray];


    // TODO (GS):
    // Do the following pre setup:
    //    ./osxtools loginitem rem /Path/To/AeroFS.app
    //    ./osxtools loginitem add /Path/To/AeroFS.app
    //
    // (osxtools is in approot)

    [[AeroController stub] getInitialStatusAndPerform:@selector(onSetupStatusReceived:error:) withObject:self];


}

+ (NSString*)appRoot
{
#ifdef DEBUG
    // For staging, assume approot is the current working dir
    return @".";
#else
    // For prod builds, assume approot is the Resources directory of the app package
    return [[NSBundle mainBundle] resourcePath];
#endif
}

- (void)onSetupStatusReceived:(GetInitialStatusReply*)reply error:(NSError*)error
{
    if (error) {
        // TODO: Have some default way to handle errors:
        // 1. log, display message, and abort program
        // 2. log and return?

        // Temporary:
        NSAssert(!error, @"Error from controller: %@", error);
    }

    switch (reply.status) {
        case GetInitialStatusReply_StatusNotLaunchable: {
            NSLog(@"NOT LAUNCHABLE - reason: %@", reply.errorMessage);
            [NSApp terminate:nil];
        }
        case GetInitialStatusReply_StatusNeedsSetup: {
            NSLog(@"RUNNING SETUP...");
            AeroSetupDialog* setupDialog = [[AeroSetupDialog alloc] init];
            [setupDialog showWindow:self];
            //[NSApp activateIgnoringOtherApps:YES];
            break;
        }
        case GetInitialStatusReply_StatusReadyToLaunch: {
            NSLog(@"LAUNCHING...");
            [[AeroController stub] launch:nil];
            break;
        }
        default:
            NSAssert(false, @"GetInitialStatusReply status code not implemented");
    }
}


@end
