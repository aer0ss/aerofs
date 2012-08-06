#import "AeroPreferencesDialog.h"

// TODO: save the pref pane that was last open

static AeroPreferencesDialog* _instance = nil;

@interface AeroPreferencesDialog (Private)
@end

@implementation AeroPreferencesDialog

- (void)dealloc
{
    NSAssert(self == _instance, @"");
    _instance = nil;
    [super dealloc];
}

/**
* returns the singleton instance of the preferences dialog
* The instance will be released automatically once the dialog is closed
*/
+ (AeroPreferencesDialog*)instance
{
    if (!_instance) {
        [[AeroPreferencesDialog alloc] init];
    }
    return _instance;
}

- (id)init
{
    NSAssert(_instance == nil, @"Trying to create more than one pref panel");
    self = [super initWithWindowNibName:NSStringFromClass([self class]) owner:self];
    _instance = self;
    if (self) {
    }

    return self;
}

- (void)awakeFromNib
{
    [self toolbarItemClicked:[[self.window.toolbar items] objectAtIndex:0]];
}

- (void)windowWillClose:(NSNotification*)notification
{
    [self release];
    [NSApp terminate:nil]; // TODO: only for debug
}

- (IBAction)toolbarItemClicked:(id)sender
{
    AeroPrefToolbarItem* item = (AeroPrefToolbarItem*)sender;

    NSView* contentView = self.window.contentView;
    NSView* prefsView = item.associatedView;

    // Remove the previous view
    if (contentView.subviews.count > 0) {
        [[contentView.subviews objectAtIndex:0] removeFromSuperview];
    }

    // Resize the window to match the height of the pref pane + toolbar
    NSRect windowFrame = self.window.frame;
    const float toolbarHeight = windowFrame.size.height - contentView.frame.size.height;
    windowFrame.size.height = prefsView.frame.size.height + toolbarHeight;
    windowFrame.origin.y = NSMaxY(self.window.frame) - (prefsView.frame.size.height + toolbarHeight);
    [self.window setFrame:windowFrame display:YES animate:YES];

    // Set the width of the pref pane to be the width of the window
    NSRect prefsFrame = prefsView.frame;
    prefsFrame.size.width = windowFrame.size.width;
    [prefsView setFrame:prefsFrame];

    // Display the pref pane
    [contentView addSubview:prefsView];
    self.window.title = item.label;
}

#pragma mark -
#pragma mark Private methods

@end

@implementation AeroPrefToolbarItem
@synthesize associatedView;
@end
