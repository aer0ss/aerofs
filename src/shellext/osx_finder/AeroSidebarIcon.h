#import <Foundation/Foundation.h>

@class NSSidebarImage;

@interface AeroSidebarIcon : NSObject {
    NSSidebarImage* _sidebarImage;
}
@property (readonly) NSSidebarImage* sidebarImage;
@end
