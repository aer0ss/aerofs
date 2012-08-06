#import "AeroMenu.h"
#import "AeroPreferencesDialog.h"

@interface AeroMenu(Private)
@end

@implementation AeroMenu

- (void)dealloc
{
    [statusItem release];
    [super dealloc];
}

- (id)init
{
    self = [super init];
    if (self) {
        // We are our own NSMenuDelegate
        self.delegate = self;
    }
    return self;
}

- (void)showInTray
{
    statusItem = [[[NSStatusBar systemStatusBar] statusItemWithLength:NSSquareStatusItemLength] retain];
    statusItem.highlightMode = YES;
    statusItem.menu = self;
    statusItem.image = [NSImage imageNamed:@"tray00.png"];
    //statusItem.alternateImage = [NSImage imageNamed:@"XXX.png"]; // TODO: set alternate image
}

// NSMenuDelegate:
- (void)menuWillOpen:(NSMenu*)menu
{
//    lol.window.level = NSStatusWindowLevel;
//    lol.window.level = NSNormalWindowLevel;
}

- (IBAction)showPreferences:(id)sender
{
    [[AeroPreferencesDialog instance] showWindow:self];
    [NSApp activateIgnoringOtherApps:YES];
}

- (IBAction)quit:(id)sender
{
    [NSApp terminate:sender];
}



@end