#import "AeroOverlay.h"
#import "AeroClient.h"

@implementation AeroOverlay {
    NSDictionary* _icons;
}

-(id)init
{
    self = [super init];
    if (!self) {
        return nil;
    }
    return self;
}

- (NSImage*)iconForPath:(NSString*)path
{
    if (![[AeroClient instance] isUnderRootAnchor:path]) {
        return nil;
    }

    Overlay status = [[AeroClient instance] overlayForPath:path];
    return [_icons objectForKey:@(status)];
}

@end
