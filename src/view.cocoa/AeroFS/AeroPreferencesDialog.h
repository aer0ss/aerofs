
@interface AeroPreferencesDialog : NSWindowController<NSWindowDelegate>

+ (AeroPreferencesDialog*) instance;
- (IBAction)toolbarItemClicked:(id)sender;

@end

/**
* All toolbar items in the preferences dialog must inherit from this class
*/
@interface AeroPrefToolbarItem : NSToolbarItem
@property (assign) IBOutlet NSView* associatedView;
@end