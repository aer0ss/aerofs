#import <Cocoa/Cocoa.h>

@class AeroSetupSignInView;
@class AeroSetupSignUpView;

@interface AeroSetupDialog : NSWindowController<NSWindowDelegate>

@property (assign) IBOutlet NSView* welcomeView;
@property (assign) IBOutlet AeroSetupSignUpView* signUpView;
@property (assign) IBOutlet AeroSetupSignInView* signInView;

@property (retain) NSString* rootAnchor;
@property (retain) NSString* deviceName;

- (IBAction)onSignUpClicked:(id)sender;
- (IBAction)onSignInClicked:(id)sender;
- (IBAction)onBackClicked:(id)sender;

@end
