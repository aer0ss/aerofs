#import "AeroGeneralPreferences.h"
#import "AeroController.h"

@implementation AeroGeneralPreferences

@synthesize version;
@synthesize username;
@synthesize deviceId;
@synthesize rootAnchor;
@synthesize dumpStats;

- (void)dealloc
{
    [version release];
    [username release];
    [deviceId release];
    [rootAnchor release];
    [dumpStats release];
    [super dealloc];
}

- (id)init
{
    self = [super init];
    if (self) {

        // request the settings from the controller
        [[[AeroController instance] stub] getConfigAndPerform:@selector(onConfigReceived:error:) withObject:self];

    }
    return self;
}

- (void)onConfigReceived:(PBConfig*)config error:(NSError*)error
{
    // TODO: Deal with error

    self.version = config.version;
    self.username = config.userName;
    self.deviceId = config.deviceId;
    self.rootAnchor = config.rootAnchor;
}

@end