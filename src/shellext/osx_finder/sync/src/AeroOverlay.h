#import <Foundation/Foundation.h>

/**
 * AeroOverlay manages the overlay icons
 */
@interface AeroOverlay : NSObject

- (NSImage*)iconForPath:(NSString*)path;

@end