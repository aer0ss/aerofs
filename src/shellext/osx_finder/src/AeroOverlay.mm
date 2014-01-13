#import <Quartz/Quartz.h>

#import "AeroOverlay.h"
#import "AeroFinderExt.h"
#import "FinderTypes.h"
#import "AeroUtils.h"

void drawOverlayForNode(const TFENode* fenode, NSRect rect);

@implementation AeroOverlay {
    NSDictionary* _icons;
}

- (void) dealloc
{
    [_icons release];
    _icons = nil;
    [super dealloc];
}

-(id)init
{
    self = [super init];
    if (!self) {
        return nil;
    }

    // Load the icons

    NSDictionary* iconNames = @{
            @(DOWNLOADING) : @"download_overlay",
            @(UPLOADING)   : @"upload_overlay",
            @(CONFLICT)    : @"conflict_overlay",
            @(IN_SYNC)     : @"in_sync_overlay",
            @(OUT_SYNC)    : @"out_of_sync_overlay"
    };

    NSBundle* aerofsBundle = [NSBundle bundleForClass:[self class]];
    _icons = [[NSMutableDictionary alloc] initWithCapacity:iconNames.count];
    for (NSNumber* key in iconNames) {
        NSImage* icon = [[[NSImage alloc] initWithContentsOfFile:
                [aerofsBundle pathForResource:[iconNames objectForKey:key] ofType:@"icns"]] autorelease];
        if (icon) [((NSMutableDictionary*)_icons) setObject:icon forKey:key];
    }

    // Swizzle methods
    [AeroUtils swizzleInstanceMethod:@selector(layerForType:) fromClass:NSClassFromString(@"IKIconCell")
                          withMethod:@selector(aero_IKIconCell_layerForType:) fromClass:[AeroOverlaySwizzledMethods class]];

    [AeroUtils swizzleInstanceMethod:@selector(drawIconWithFrame:) fromClass:NSClassFromString(@"TListViewIconAndTextCell")
                          withMethod:@selector(aero_TListViewIconAndTextCell_drawIconWithFrame:) fromClass:[AeroOverlaySwizzledMethods class]];

    // OS X 10.9
    if ([(NSClassFromString(@"TDimmableIconImageView")) instancesRespondToSelector:@selector(drawRect:)]) {
        [AeroUtils swizzleInstanceMethod:@selector(drawRect:) fromClass:NSClassFromString(@"TDimmableIconImageView")
                withMethod:@selector(aero_TDimmableIconImageView_drawRect:) fromClass:[AeroOverlaySwizzledMethods class]];
    }

    return self;
}

- (NSImage*)iconForPath:(NSString*)path
{
    if (![[AeroFinderExt instance] isUnderRootAnchor:path]) {
        return nil;
    }

    Overlay status = [[AeroFinderExt instance] overlayForPath:path];
    return [_icons objectForKey:@(status)];
}

@end

@implementation AeroOverlaySwizzledMethods

/**
* Draw overlay icons in Icon View (OS X 10.6 - 10.9)
*/
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

        NSImage* icon = [[[AeroFinderExt instance] overlay] iconForPath:path];
        overlay.contents = (id) [icon CGImageForProposedRect:&imageFrame context:nil hints:nil];
        [result addSublayer:overlay];

        return result;
    } @catch (NSException* ex) {
        NSLog(@"AeroFS: Exception in aero_IKIconCell_layerForType: %@ %@", ex, [ex callStackSymbols]);
        return nil;
    }
}

/**
* Draw overlay icons for both List View and Column View in 10.6 - 10.8
* Draw overlay icons for Column View only in 10.9
*/
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
        NSRect imageFrame = [_self imageRectForBounds: NSRectFromCGRect(frame)];
        drawOverlayForNode(fenode, imageFrame);
    }
    @catch (NSException* ex) {
        NSLog(@"AeroFS: Exception in aero_TListViewIconAndTextCell_drawIconWithFrame: %@ %@", ex, [ex callStackSymbols]);
    }
}

/**
* Draw overlay icons in List View in 10.9
* This method does not exist pre-10.9
*/
- (void)aero_TDimmableIconImageView_drawRect:(NSRect)dirtyRect
{
    @try {
        // Call original implementation
        [self aero_TDimmableIconImageView_drawRect:dirtyRect];

        if (![[AeroFinderExt instance] shouldModifyFinder]) {
            return;
        }

        TDimmableIconImageView* _self = (TDimmableIconImageView*) self;
        TListRowView* rowView = (TListRowView*)[[_self superview] superview];

        TFENode fenode = [rowView node];
        drawOverlayForNode(&fenode, dirtyRect);
    }
    @catch (NSException* ex) {
        NSLog(@"AeroFS: Exception in aero_TDimmableIconImageView_drawRect: %@ %@", ex, [ex callStackSymbols]);
    }
}

void drawOverlayForNode(const TFENode* fenode, NSRect rect)
{
    // Get the path from the node
    id finode = FINodeFromFENode(fenode);
    NSString* path = [[finode previewItemURL] path];

    // Draw the overlay
    NSImage* icon = [[[AeroFinderExt instance] overlay] iconForPath:path];
    [icon drawInRect:rect fromRect:NSZeroRect operation:NSCompositeSourceOver fraction:1.0 respectFlipped:YES hints:nil];
}

@end

