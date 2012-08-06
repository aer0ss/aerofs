#import "AeroSetupStatusView.h"

@implementation AeroSetupStatusView

@synthesize image, progressIndicator, label;

- (void)setError:(NSString*)msg
{
    [progressIndicator stopAnimation:self];
    image.image = [NSImage imageNamed:@"exclamation.png"];
    [image setHidden:NO];
    label.stringValue = msg;
}

- (void)setSuccess:(NSString*)msg
{
    [progressIndicator stopAnimation:self];
    image.image = [NSImage imageNamed:@"tick.png"];
    label.stringValue = msg;
}

- (void)setProgress:(NSString*)msg
{
    [progressIndicator startAnimation:self];
    [image setHidden:YES];
    label.stringValue = msg;
}

- (void)clear
{
    [progressIndicator stopAnimation:self];
    [image setHidden:YES];
    label.stringValue = @"";
}

@end