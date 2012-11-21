#import <Quartz/Quartz.h>

#import "AeroOverlay.h"
#import "AeroFinderExt.h"
#import "FinderTypes.h"
#import "AeroUtils.h"
#import "AeroIconPair.h"

@implementation AeroOverlay

- (void) dealloc
{
    [dlIcon release];
    [ulIcon release];
    [isIcon release];
    [psIcon release];
    [osIcon release];

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

    dlIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"download_overlay" ofType:@"icns"]];
    ulIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"upload_overlay" ofType:@"icns"]];
    cfIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"conflict_overlay" ofType:@"icns"]];

    isIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"in_sync_overlay" ofType:@"icns"]];
    psIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"partial_sync_overlay" ofType:@"icns"]];
    osIcon = [[AeroIconPair alloc] initWithContentsOfFile: [aerofsBundle pathForResource:@"out_of_sync_overlay" ofType:@"icns"]];

    // Swizzlle methods
    [AeroUtils swizzleInstanceMethod:@selector(layerForType:) fromClass:NSClassFromString(@"IKIconCell")
                          withMethod:@selector(aero_IKIconCell_layerForType:) fromClass:[AeroOverlaySwizzledMethods class]];

    [AeroUtils swizzleInstanceMethod:@selector(drawIconWithFrame:) fromClass:NSClassFromString(@"TListViewIconAndTextCell")
                          withMethod:@selector(aero_TListViewIconAndTextCell_drawIconWithFrame:) fromClass:[AeroOverlaySwizzledMethods class]];

    return self;
}

-(NSImage*) iconForPath:(NSString*)path flipped:(BOOL)flipped
{
    if (![[AeroFinderExt instance] isUnderRootAnchor:path]) {
        return nil;
    }

    //NSLog(@"overlay pull: %@ [%@]", path, [NSThread callStackSymbols]);

    AeroIconPair* result = nil;
    Overlay status = [[AeroFinderExt instance] overlayForPath:path];
    switch (status) {
        case IN_SYNC:       result = isIcon; break;
        case PARTIAL_SYNC:  result = psIcon; break;
        case OUT_SYNC:      result = osIcon; break;
        case DOWNLOADING:   result = dlIcon; break;
        case UPLOADING:     result = ulIcon; break;
        case CONFLICT:      result = cfIcon; break;
        default: break;
    }

    return flipped ? [result flipped] : [result icon];
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

