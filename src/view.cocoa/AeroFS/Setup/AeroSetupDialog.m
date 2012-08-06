#import "AeroSetupDialog.h"
#import "AeroController.h"
#import "AeroSetupSignUpView.h"
#import "AeroSetupSignInView.h"

// Private properties and methods
@interface AeroSetupDialog()

typedef enum {
    NoAnimation,
    ForwardAnimation,
    BackwardAnimation,
} Animation;

- (void)showView:(NSView*)newView withAnimation:(Animation)animation;

@end

@implementation AeroSetupDialog

@synthesize welcomeView;
@synthesize signUpView;
@synthesize signInView;
@synthesize rootAnchor;
@synthesize deviceName;

- (void)dealloc
{
    [rootAnchor release];
    [deviceName release];
    [super dealloc];
}

- (id)init
{
    self = [super initWithWindowNibName:NSStringFromClass([self class]) owner:self];
    if (self) {
        // Get the default settings
        [[AeroController stub] getSetupSettings:^(GetSetupSettingsReply* reply, NSError* error) {
            self.rootAnchor = reply.rootAnchor;
            self.deviceName = reply.deviceName;
        }];
    }
    return self;
}

- (void)awakeFromNib
{
    signUpView.setupDialog = self;
    signInView.setupDialog = self;
    [self showView:welcomeView withAnimation:NoAnimation];
}

- (IBAction)onSignInClicked:(id)sender
{
    [self showView:signInView withAnimation:ForwardAnimation];
}

- (IBAction)onSignUpClicked:(id)sender
{
    [self showView:signUpView withAnimation:ForwardAnimation];
}

- (IBAction)onBackClicked:(id)sender
{
    [self showView:welcomeView withAnimation:BackwardAnimation];
}

- (void)windowWillClose:(NSNotification*)notification
{
    [self release];
    [NSApp terminate:nil];
}

- (void)showView:(NSView*)newView withAnimation:(Animation)animation
{
    NSView* contentView = self.window.contentView;

    // If we don't want an animation, or if there is no previous view, add this view and return
    if (animation == NoAnimation || contentView.subviews.count == 0) {
        contentView.subviews = [NSArray array];
        [contentView addSubview:newView];
        return;
    }

    // Otherwise, add the view with an animated transition
    BOOL forward = (animation == ForwardAnimation);

    NSRect centerFrame = contentView.frame;
    NSRect leftFrame   = centerFrame;
    NSRect rightFrame  = centerFrame;

    leftFrame.origin.x  -= centerFrame.size.width;
    rightFrame.origin.x += centerFrame.size.width;

    // Add the new view and position it outside the window
    [newView setFrame:(forward) ? rightFrame : leftFrame];
    [contentView addSubview:newView];

    // Animate the new view so that it comes to the center frame
    NSMutableDictionary* newViewDict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
            newView, NSViewAnimationTargetKey,
            [NSValue valueWithRect:contentView.frame], NSViewAnimationEndFrameKey,
            nil];

    NSView* oldView = [contentView.subviews objectAtIndex:0];
    NSValue* oldViewEndFrame = [NSValue valueWithRect:(forward) ? leftFrame : rightFrame];

    // Animate the old view so that it goes outside the window
    NSMutableDictionary* oldViewDict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
            oldView,           NSViewAnimationTargetKey,
            oldViewEndFrame,   NSViewAnimationEndFrameKey,
            nil];

    // Start the animation
    NSViewAnimation* anim = [[[NSViewAnimation alloc] init] autorelease];
    anim.viewAnimations = [NSArray arrayWithObjects:oldViewDict, newViewDict, nil];
    anim.animationBlockingMode = NSAnimationBlocking;
    anim.duration = 0.3; // 300 ms
    [anim startAnimation];

    [oldView removeFromSuperview];

    if ([newView respondsToSelector:@selector(viewDidShow:)]) {
        [newView performSelector:@selector(viewDidShow:) withObject:self];
    }
}

@end


