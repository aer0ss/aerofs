#import <Cocoa/Cocoa.h>

@interface AeroContextMenu : NSObject

-(id)init;

@end

@interface AeroContextMenuSwizzledMethods : NSObject

+(void) aero_addViewSpecificStuffToMenu:(NSMenu*)menu browserViewController:(id)browserVC context:(unsigned int)ctx;

@end

