#import "AeroSidebarIcon.h"
#import "AeroUtils.h"
#import <objc/message.h>
#import "FinderTypes.h"
#import "AeroFinderExt.h"

// Holds the original implementation of Finder's [TSidebarItemCell setImage:]
static IMP gOriginalSetImage;

// Our new implementation
static void aero_TSidebarItemCell_setImage(TSidebarItemCell* self, SEL sel, NSImage* image);

static Class TSidebarItemCellClass;

@implementation AeroSidebarIcon


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

            // Note: we can't use method swizzling here because Dropbox does something stupid that
            // makes it impossible for any other app to perform swizzling if they swizzle first.
            // See comment below for more information.
            TSidebarItemCellClass = NSClassFromString(@"TSidebarItemCell");
            gOriginalSetImage = replace_method(TSidebarItemCellClass,
                    @selector(setImage:), (IMP)aero_TSidebarItemCell_setImage);
        }
    }

    return self;
}

@end

/**
* This is called when the Finder sets the image to be displayed for a given TSidebarItemCell
* We check if the name of the cell is equal to "AeroFS", and if so, we change the image to be our
* custom icon.
*/
void aero_TSidebarItemCell_setImage(TSidebarItemCell* self, SEL sel, NSImage* image)
{
    @try {
        if ([self.name isEqualToString:@"AeroFS"]) {
            NSImage* aerofsIcon = [[[AeroFinderExt instance] sidebarIcon] sidebarImage];
            if (aerofsIcon) image = aerofsIcon;
        }

        // Now, we want to call the original [setImage:] implementation. There are two possibilities:
        // 1. Either [setImage:] wasn't actually implemented by TSidebarItemCell, but by one of its
        // superclasses. In this case, gOriginalSetImage will be null, and we want to call the
        // superclass implementation. This is the normal case.
        // 2. Or [setImage:] was implemented in TSidebarItemCell, in which case we just have to call
        // gOriginalSetImage(). This would be the case if, for example, another Finder extension
        // like Dropbox's has previously done the exact same thing as what we are doing here.
        if (!gOriginalSetImage) {
            // Case 1. (Normal case)
            // Does the equivalent of `[super setImage:image]`
            struct objc_super super = {self, [TSidebarItemCellClass superclass]};
            objc_msgSendSuper(&super, @selector(setImage:), image);
        } else {
            // Case 2. (Dropbox or some other app is also swizzling this method)
            gOriginalSetImage(self, @selector(setImage:), image);
        }
    } @catch (NSException* ex) {
        NSLog(@"AeroFS: Exception in aero_TSidebarItemCell_setImage : %@ %@", ex, [ex callStackSymbols]);
    }
}

