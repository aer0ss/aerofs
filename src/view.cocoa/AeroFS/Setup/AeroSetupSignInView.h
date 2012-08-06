
@class AeroSetupStatusView;
@class AeroSetupDialog;

@interface AeroSetupSignInView : NSView

@property (assign) IBOutlet NSView* statusView;
@property (assign) IBOutlet NSButton* finishButton;
@property (assign) AeroSetupDialog* setupDialog;
@property(nonatomic, assign) BOOL setupInProgress;
@property (assign) IBOutlet AeroSetupStatusView* status;
@property (assign) IBOutlet NSTextField* emailAddress;
@property (assign) IBOutlet NSSecureTextField* password;

- (IBAction)onFieldFilled:(id)sender;
- (IBAction)onFinishClicked:(id)sender;
- (void)viewDidShow:(id)sender;
@end