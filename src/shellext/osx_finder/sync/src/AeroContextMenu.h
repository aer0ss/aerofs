#import <Cocoa/Cocoa.h>

@interface AeroContextMenu : NSObject

-(id)init;
+(NSMenu*)subMenuForPath:(NSString*)path;

@end