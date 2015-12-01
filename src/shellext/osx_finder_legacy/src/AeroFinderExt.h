#import <Cocoa/Cocoa.h>
#import "AeroOverlayCache.h"

@class AeroSocket;
@class AeroOverlay;
@class AeroContextMenu;
@class ShellextNotification;
@class PathStatusNotification;
@class AeroSidebarIcon;

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


@interface AeroFinderExt : NSObject <AeroEvictionDelegate> {
@private
	AeroSocket* socket;
	AeroOverlay* overlay;
	AeroContextMenu* contextMenu;
	AeroSidebarIcon* sidebarIcon;
	NSString* rootAnchor;
	NSString* userId;
	AeroOverlayCache* statusCache;

	NSTimer* refreshTimer;
	NSTimeInterval lastRefreshTime;
	BOOL isLinkSharingEnabled;
}

@property (readonly) AeroOverlay* overlay;
@property (readonly) AeroContextMenu* contextMenu;
@property (readonly) AeroSidebarIcon* sidebarIcon;
@property (readonly) BOOL isLinkSharingEnabled;

OSErr AeroLoadHandler(const AppleEvent* ev, AppleEvent* reply, long refcon);

+ (AeroFinderExt*)instance;
- (id)init;
- (void)reconnect:(NSString*)sockFile;
- (BOOL)isUnderRootAnchor:(NSString*)path;
- (int)flagsForPath:(NSString*)path;
- (BOOL)shouldModifyFinder;
- (BOOL)shouldEnableTestingFeatures;
- (void)showShareFolderDialog:(id)sender;
- (void)showSyncStatusDialog:(id)sender;
- (void)showVersionHistoryDialog:(id)sender;
- (void)showConflictResolutionDialog:(id)sender;
- (void)createLink:(id)sender;
- (void)sendGreeting;
- (void)parseNotification:(ShellextNotification*)notification;
- (void)onStatus:(PathStatusNotification*)notification;
- (Overlay)overlayForPath:(NSString*)path;

@end
