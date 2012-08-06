#import <Cocoa/Cocoa.h>

@class AeroNode;
@class AeroIconPair;

/**
 AeroOverlay manages the overlay icons
 Main tasks:
 - keeping track of statuses associated with file names
 - drawing the appropriate icons for the files
 */
@interface AeroOverlay : NSObject {
@private
    AeroNode* rootNode;
    AeroIconPair* dlIcon;
    AeroIconPair* ulIcon;
}

@property (readonly) AeroNode* rootNode;

-(NSImage*) iconForPath:(NSString*)path flipped:(BOOL)flipped;
- (void)clearCache:(NSString*)rootAnchor;

@end

/**
 Contains the Finder methods that we are going to swizzle
 We put them in a separate class because they are technically not part of this
 class. They belong to the class they will be injected into.
 */
@interface AeroOverlaySwizzledMethods : NSObject

-(CALayer*) aero_IKIconCell_layerForType:(NSString*)type;
-(void)aero_TListViewIconAndTextCell_drawIconWithFrame:(CGRect) frame;

@end
