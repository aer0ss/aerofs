
@class AeroSetupStatusView;
@class AeroSetupDialog;

@interface AeroSetupSignUpView : NSView {
@private
    BOOL isSignUpCodeValid;
    BOOL passwordsMatch;
    BOOL setupInProgress;
}

@property (assign) IBOutlet NSView* statusView;
@property (assign) IBOutlet NSSecureTextField* password2;
@property (assign) IBOutlet NSProgressIndicator* icProgress;
@property (assign) IBOutlet NSImageView* icStatusImg;
@property (assign) IBOutlet NSImageView* pwStatusImg;
@property (assign) IBOutlet NSButton* finishButton;
@property (assign) AeroSetupDialog* setupDialog;
@property(nonatomic, assign) BOOL setupInProgress;
@property (assign) IBOutlet AeroSetupStatusView* status;
@property (assign) IBOutlet NSTextField* signUpCode;
@property (assign) IBOutlet NSTextField* emailAddress;
@property (assign) IBOutlet NSTextField* firstName;
@property (assign) IBOutlet NSTextField* lastName;
@property (assign) IBOutlet NSSecureTextField* password;


- (IBAction)onFinishClicked:(id)sender;
- (IBAction)onSignUpCodeChanged:(id)sender;
- (IBAction)onPasswordsChanged:(id)sender;
- (IBAction)onFieldFilled:(id)sender;
- (void)viewDidShow:(id)sender;

@end