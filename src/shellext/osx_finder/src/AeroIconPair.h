#import <Cocoa/Cocoa.h>

/**
 Holds an icon and it's flipped copy
 */
@interface AeroIconPair : NSObject {
@private
    NSImage*  icon;
    NSImage* flipped;
}

@property (readonly) NSImage* icon;
@property (readonly) NSImage* flipped;
- (id)initWithContentsOfFile:(NSString*)fileName;
+ (id)iconPairWithContentsOfFile:(NSString*)fileName;
@end
