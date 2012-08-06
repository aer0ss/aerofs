#import "Categories.h"

@implementation NSString(IsEmpty)
- (BOOL)isEmpty
{
    return (self.length == 0);
}
@end

@implementation NSView(SetFocus)
- (void)setFocus
{
    [self.window makeKeyAndOrderFront:nil];
    [self.window makeFirstResponder:self];
}
@end
