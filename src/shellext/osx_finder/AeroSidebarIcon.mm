#import "AeroSidebarIcon.h"
#import "AeroUtils.h"
#import "FinderTypes.h"
#import "AeroFinderExt.h"

@interface AeroSidebarIconSwizzledMethods : NSObject
@end

@implementation AeroSidebarIcon

- (void)dealloc
{
    [_sidebarImage release];
    [super dealloc];
}

- (id)init
{
    self = [super init];
    if (self) {
        // Check that we're running at least OS X Lion. We do not support sidebar icons before Lion.
        if (NSAppKitVersionNumber >= NSAppKitVersionNumber10_7) {

            // Initialize our sidebar image
            NSBundle* aerofsBundle = [NSBundle bundleForClass:[self class]];
            NSImage* image = [[NSImage alloc] initWithContentsOfFile:[aerofsBundle pathForResource:@"sidebar" ofType:@"tiff"]];
            [image setTemplate:YES];
            _sidebarImage = [[NSSidebarImage alloc] initWithSourceImage:image];
            [image release];

            // Swizzle methods
            [AeroUtils swizzleInstanceMethod:@selector(setImage:)
                    fromClass:NSClassFromString(@"TSidebarItemCell")
                    withMethod:@selector(aero_TSidebarItemCell_setImage:)
                    fromClass:[AeroSidebarIconSwizzledMethods class]];
        }
    }

    return self;
}

@end

@implementation AeroSidebarIconSwizzledMethods

/**
* This is called when the Finder sets the image to be displayed for a given TSidebarItemCell
* We check if the name of the cell is equal to "AeroFS", and if so, we change the image to be our
* custom icon.
*/
- (void)aero_TSidebarItemCell_setImage:(NSImage*)image
{
    @try {
        TSidebarItemCell* _self = (TSidebarItemCell*)self;
        if ([_self.name isEqualToString:@"AeroFS"]) {
            image = [[[AeroFinderExt instance] sidebarIcon] sidebarImage];
        }
        [self aero_TSidebarItemCell_setImage:image];
    } @catch (NSException* exception) {
        NSLog(@"AeroFS: Exception in aero_TSidebarItemCell_setImage: %@", exception);
    }
}

@end