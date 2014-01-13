#import <Foundation/Foundation.h>

/**
 * AeroOverlay manages the overlay icons
 */
@interface AeroOverlay : NSObject

- (NSImage*)iconForPath:(NSString*)path;

@end

/**
 Contains the Finder methods that we are going to swizzle
 We put them in a separate class because they are technically not part of this
 class. They belong to the class they will be injected into.
 */
@interface AeroOverlaySwizzledMethods : NSObject

-(CALayer*) aero_IKIconCell_layerForType:(NSString*)type;
-(void)aero_TListViewIconAndTextCell_drawIconWithFrame:(CGRect) frame;
-(void)aero_TDimmableIconImageView_drawRect:(NSRect)dirtyRect;

@end
