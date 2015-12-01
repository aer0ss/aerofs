#import <Cocoa/Cocoa.h>
#import "../../../osx_common/src/AeroOverlayCache.h"

@class AeroSocket;
@class AeroOverlay;
@class AeroContextMenu;
@class ShellextNotification;
@class PathStatusNotification;

typedef enum {
    NONE,
    OUT_SYNC,
    IN_SYNC,
    DOWNLOADING,
    UPLOADING,
    CONFLICT,

    OverlayCount
} Overlay;

// flags are bitwise OR'ed
typedef enum {
    RootAnchor = 1 << 0,
    Directory  = 1 << 1,
    File       = 1 << 2
} PathFlag;

@interface AeroClient : NSObject <AeroEvictionDelegate> {
@private
    AeroSocket* socket;
    AeroOverlay* overlay;
    AeroContextMenu* contextMenu;
    NSString* rootAnchor;
    NSString* userId;
    AeroOverlayCache* statusCache;

    NSTimer* refreshTimer;
    NSTimeInterval lastRefreshTime;
    BOOL isLinkSharingEnabled;
}

@property (readonly) AeroOverlay* overlay;
@property (readonly) AeroContextMenu* contextMenu;
@property (readonly) BOOL isLinkSharingEnabled;
@property (readonly) NSString* rootAnchor;

+ (AeroClient*)instance;
- (id)init;
- (BOOL)isConnected;
- (void)reconnect:(NSString*)sockFile;
- (void)disconnect;
- (BOOL)isUnderRootAnchor:(NSString*)path;
- (int)flagsForPath:(NSString*)path;
- (NSImage*)iconForPath:(NSString*)path;
- (BOOL)shouldModifyFinder;
- (BOOL)shouldEnableTestingFeatures;
- (void)showShareFolderDialog:(NSString*)path;
- (void)showSyncStatusDialog:(NSString*)path;
- (void)showVersionHistoryDialog:(NSString*)path;
- (void)showConflictResolutionDialog:(NSString*)path;
- (void)createLink:(NSString*)path;
- (void)sendGreeting;
- (void)parseNotification:(ShellextNotification*)notification;
- (void)onStatus:(PathStatusNotification*)notification;
- (Overlay)overlayForPath:(NSString*)path;

@end
