#import <Quartz/Quartz.h>

#import "AeroOverlay.h"
#import "AeroFinderExt.h"
#import "FinderTypes.h"
#import "AeroNode.h"
#import "AeroUtils.h"
#import "AeroIconPair.h"

@implementation AeroOverlay

@synthesize rootNode;

- (void) dealloc
{
    [rootNode release];
    [dlIcon release];
    [ulIcon release];

    [super dealloc];
}

-(id)init
{
    self = [super init];
    if (!self) {
        return nil;
    }

    // Load the icons
    NSBundle* aerofsBundle = [NSBundle bundleForClass:[self class]];

    dlIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"DownloadBadge" ofType:@"icns"]];
    ulIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"UploadBadge" ofType:@"icns"]];

    // Swizzlle methods
    [AeroUtils swizzleInstanceMethod:@selector(layerForType:) fromClass:NSClassFromString(@"IKIconCell")
                          withMethod:@selector(aero_IKIconCell_layerForType:) fromClass:[AeroOverlaySwizzledMethods class]];

    [AeroUtils swizzleInstanceMethod:@selector(drawIconWithFrame:) fromClass:NSClassFromString(@"TListViewIconAndTextCell")
                          withMethod:@selector(aero_TListViewIconAndTextCell_drawIconWithFrame:) fromClass:[AeroOverlaySwizzledMethods class]];

    return self;
}

- (void)clearCache:(NSString*)rootAnchor
{
    [rootNode release];
    if (rootAnchor.length > 0) {
        rootNode = [[AeroNode alloc] initWithName:rootAnchor andParent:nil];
    } else {
        rootNode = nil;
    }
}

-(NSImage*) iconForPath:(NSString*)path flipped:(BOOL)flipped
{
    AeroNode* node = [rootNode getNodeAtPath:path createPath:NO];

    if (node == nil) {
        return nil;
    }

    AeroIconPair* result = nil;

    if (node.status & Downloading) result = dlIcon;
    if (node.status & Uploading) result = ulIcon;

    return flipped? [result flipped] : [result icon];
}

@end

@implementation AeroOverlaySwizzledMethods

-(CALayer*) aero_IKIconCell_layerForType:(NSString*)type
{
    @try {
        // Call the original implementation
        CALayer* result = [self aero_IKIconCell_layerForType:type];

        // To avoid compiler warnings, we cast self to it's actual runtime type
        IKImageBrowserCell* _self = (IKImageBrowserCell*) self;

        if (![type isEqualToString:IKImageBrowserCellForegroundLayer]
            || [_self cellState] != IKImageStateReady
            || ![[AeroFinderExt instance] shouldModifyFinder]) {
            return result;
        }

        id finode = [_self representedItem];
        if (finode == nil || ![finode respondsToSelector:@selector(previewItemURL)]) {
            return result;
        }
        NSString* path = [[finode previewItemURL] path];

        NSRect frame = [_self frame];
        NSRect imageFrame = [_self imageFrame];

        if (result == nil) {
            result = [CALayer layer];
            result.frame = NSRectToCGRect(frame);
        }

        // Make imageFrame coordinates relative to frame
        imageFrame.origin.x -= frame.origin.x;
        imageFrame.origin.y -= frame.origin.y;

        CALayer* overlay = [CALayer layer];
        overlay.frame = NSRectToCGRect(imageFrame);

        NSImage* icon = [[[AeroFinderExt instance] overlay] iconForPath:path flipped:NO];
        overlay.contents = (id) [icon CGImageForProposedRect:&imageFrame context:nil hints:nil];
        [result addSublayer:overlay];

        return result;
    } @catch (NSException* exception) {
        NSLog(@"AeroFS: Exception in aero_IKIconCell_layerForType: %@", exception);
        return nil;
    }
}

-(void)aero_TListViewIconAndTextCell_drawIconWithFrame:(CGRect) frame
{
    @try {
        // Call original implementation
        [self aero_TListViewIconAndTextCell_drawIconWithFrame:frame];

        if (![[AeroFinderExt instance] shouldModifyFinder]) {
            return;
        }

        NSCell* _self = (NSCell*) self;

        // Get the path to this node
        const TFENode* fenode = [_self node];
        id finode = FINodeFromFENode(fenode);
        NSString* path = [[finode previewItemURL] path];

        NSImage* icon = [[[AeroFinderExt instance] overlay] iconForPath:path flipped:YES];

        // Draw the overlay
        NSGraphicsContext* nsgc = [NSGraphicsContext currentContext];
        CGContextRef context = (CGContextRef) [nsgc graphicsPort];
        NSRect imageFrame = [_self imageRectForBounds: NSRectFromCGRect(frame)];
        CGContextDrawImage(context, NSRectToCGRect(imageFrame), [icon CGImageForProposedRect:&imageFrame context:nil hints:nil]);
    }
    @catch (NSException* exception) {
        NSLog(@"AeroFS: Exception in aero_TListViewIconAndTextCell_drawIconWithFrame: %@", exception);
    }
}

@end

