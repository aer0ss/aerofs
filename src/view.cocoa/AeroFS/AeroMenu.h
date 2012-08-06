#import <Foundation/Foundation.h>

/**
* This class represents the AeroFS menu that sits in the OS X menu bar
*/
@interface AeroMenu : NSMenu<NSMenuDelegate> {
@private
    NSStatusItem* statusItem; // tray icon
}

- (void)showInTray;

- (IBAction)showPreferences:(id)sender;
- (IBAction)quit:(id)sender;

@end