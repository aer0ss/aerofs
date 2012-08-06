#import "AeroIconPair.h"

@implementation AeroIconPair

@synthesize icon;
@synthesize flipped;

-(void)dealloc
{
    [icon release];
    [flipped release];
    [super dealloc];
}

- (id)initWithContentsOfFile:(NSString*)fileName
{
    self = [super init];
    if (!self) {
        return nil;
    }

    icon = [[NSImage alloc] initWithContentsOfFile:fileName];
    flipped = [icon copy];
    [flipped setFlipped:YES];

    return self;
}

+ (id)iconPairWithContentsOfFile:(NSString*)fileName
{
    return [[[AeroIconPair alloc] initWithContentsOfFile:fileName] autorelease];
}

@end

