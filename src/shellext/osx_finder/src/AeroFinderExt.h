#import <Cocoa/Cocoa.h>

@class AeroSocket;
@class AeroOverlay;
@class AeroContextMenu;
@class ShellextNotification;
@class FileStatusNotification;

// flags are bitwise OR'ed
typedef enum {
    RootAnchor = 1 << 0,
    Directory  = 1 << 1
} PathFlag;

@interface AeroFinderExt : NSObject {
@private
    AeroSocket* socket;
    AeroOverlay* overlay;
    AeroContextMenu* contextMenu;
    NSString* rootAnchor;
    NSString* userId;
}

@property (readonly) AeroOverlay* overlay;
@property (readonly) AeroContextMenu* contextMenu;

OSErr AeroLoadHandler(const AppleEvent* ev, AppleEvent* reply, long refcon);

+ (AeroFinderExt*)instance;
- (id)init;
- (void)reconnect:(UInt16)port;
- (BOOL)isUnderRootAnchor:(NSString*)path;
- (int)flagsForPath:(NSString*)path;
- (BOOL)shouldModifyFinder;
- (BOOL)shouldEnableTestingFeatures;
- (void)showShareFolderDialog:(id)sender;
- (void)showSyncStatusDialog:(id)sender;
- (void)showVersionHistoryDialog:(id)sender;
- (void)sendGreeting;
- (void)parseNotification:(ShellextNotification*)notification;
- (void)onFileStatus:(FileStatusNotification*)notification;

@end
