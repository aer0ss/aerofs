//
//  FinderSync.h
//  sync
//

#import <Cocoa/Cocoa.h>
#import <FinderSync/FinderSync.h>
#import "AeroClient.h"
#import "AeroContextMenu.h"

@interface AeroFinderSync : FIFinderSync

+ (void)initMyFolderURL;
+ (void)refreshBadgeImageWithStatus:(Overlay)status :(NSURL*)url;

@end
