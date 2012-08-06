#import <Cocoa/Cocoa.h>

@class AeroMenu;

@interface AeroFSAppDelegate : NSObject <NSApplicationDelegate> {
}

@property (assign) IBOutlet AeroMenu* trayMenu;

+ (NSString*)appRoot;

@end
