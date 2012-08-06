#import "AeroSetupSignInView.h"
#import "AeroSetupStatusView.h"
#import "Categories.h"
#import "AeroSetupDialog.h"
#import "AeroController.h"

@interface AeroSetupSignInView()

- (void)computeFinishButtonState;

@end

@implementation AeroSetupSignInView

@synthesize status;
@synthesize statusView;
@synthesize emailAddress;
@synthesize password;
@synthesize finishButton;
@synthesize setupDialog;
@synthesize setupInProgress;

- (void)awakeFromNib
{
    [self computeFinishButtonState];
}

- (void)viewDidShow:(id)sender
{
    [self computeFinishButtonState];
    [statusView addSubview:status];
    [status clear];
    [emailAddress setFocus];
}

- (void)controlTextDidChange:(NSNotification*)notification
{
    [self computeFinishButtonState];
}

- (IBAction)onFieldFilled:(id)sender
{
    [self computeFinishButtonState];
}

- (void)setSetupInProgress:(BOOL)inProgress
{
    setupInProgress = inProgress;
    [self computeFinishButtonState];
}

- (void)onFinishClicked:(id)sender
{
    self.setupInProgress = YES;

    [status setProgress:TR(@"Setting up...", nil)];

    [[AeroController stub] setupExistingUser:emailAddress.stringValue
                                withPassword:password.stringValue
                              withRootAnchor:setupDialog.rootAnchor
                              withDeviceName:setupDialog.deviceName
                                  andPerform:@selector(setupDone:) withObject:setupDialog];

    //                             usingBlock:^(Void* r, NSError* error) {
    //
    //                                    self.setupInProgress = NO;
    //                                    if (error) {
    //                                        [status setError:error.localizedDescription];
    //                                    } else {
    //                                        [status setSuccess:TR(@"Launching AeroFS...", nil)];
    //                                    }
    //                             }];

}

- (void)computeFinishButtonState
{
    BOOL state = (!emailAddress.stringValue.isEmpty && !password.stringValue.isEmpty);
    [finishButton setEnabled:state];
    [finishButton setKeyEquivalent:@"\r"];
}

@end