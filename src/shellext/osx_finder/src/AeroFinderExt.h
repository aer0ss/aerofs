#import <Cocoa/Cocoa.h>

@class AeroSocket;
@class AeroOverlay;
@class AeroContextMenu;
@class ShellextNotification;
@class FileStatusNotification;

@interface AeroFinderExt : NSObject {
@private
    AeroSocket* socket;
    AeroOverlay* overlay;
    AeroContextMenu* contextMenu;
    NSString* rootAnchor;
}

@property (readonly) AeroOverlay* overlay;
@property (readonly) AeroContextMenu* contextMenu;

OSErr AeroLoadHandler(const AppleEvent* ev, AppleEvent* reply, long refcon);

+ (AeroFinderExt*)instance;
- (id)init;
- (void)reconnect:(UInt16)port;
- (BOOL)shouldDisplayContextMenu:(NSString*)path;
- (BOOL)shouldModifyFinder;
- (void)showShareFolderDialog:(id)sender;
- (void)sendGreeting;
- (void)parseNotification:(ShellextNotification*)notification;
- (void)onFileStatus:(FileStatusNotification*)notification;

@end
