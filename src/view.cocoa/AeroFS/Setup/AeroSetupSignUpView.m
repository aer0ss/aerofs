#import "AeroSetupSignUpView.h"
#import "AeroSetupStatusView.h"
#import "AeroController.h"
#import "Categories.h"
#import "AeroSetupDialog.h"

@interface AeroSetupSignUpView()

@property(nonatomic, assign) BOOL isSignUpCodeValid;
@property(nonatomic, assign) BOOL passwordsMatch;

- (void)computeFinishButtonState;
- (void) setStatusImage:(NSImageView*)imgView successful:(BOOL)success;
@end

@implementation AeroSetupSignUpView

@synthesize setupDialog;
@synthesize status;
@synthesize statusView;
@synthesize signUpCode;
@synthesize emailAddress;
@synthesize firstName;
@synthesize lastName;
@synthesize password;
@synthesize password2;
@synthesize icProgress;
@synthesize icStatusImg;
@synthesize pwStatusImg;
@synthesize finishButton;
@synthesize passwordsMatch;
@synthesize isSignUpCodeValid;
@synthesize setupInProgress;


- (void)awakeFromNib
{
    self.isSignUpCodeValid = NO;
    [self setPasswordsMatch:NO];
}

/**
* Called by the setup dialog each time this view is displayed
*/
- (void)viewDidShow:(id)sender
{
    [self computeFinishButtonState];
    [statusView addSubview:status];
    [status clear];
    [signUpCode setFocus];
}

- (IBAction)onFieldFilled:(id)sender
{
    [self computeFinishButtonState];
}

- (void)setIsSignUpCodeValid:(BOOL)valid
{
    isSignUpCodeValid = valid;
    [self computeFinishButtonState];
}

- (void)setPasswordsMatch:(BOOL)match
{
    passwordsMatch = match;
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

    [[AeroController stub] setupNewUser:emailAddress.stringValue
                           withPassword:password.stringValue
                         withRootAnchor:setupDialog.rootAnchor
                         withDeviceName:setupDialog.deviceName
                         withSignUpCode:signUpCode.stringValue
                          withFirstName:firstName.stringValue
                           withLastName:lastName.stringValue
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

- (IBAction)onSignUpCodeChanged:(id)sender
{
    [icProgress startAnimation:self];
    [icStatusImg setHidden:YES];
    self.isSignUpCodeValid = NO;

    [[AeroController stub] resolveSignUpCode:signUpCode.stringValue usingBlock:
        ^(ResolveSignUpCodeReply* reply, NSError* error) {

            [icProgress stopAnimation:self];
            [emailAddress setEnabled:YES];

            // If the user has erased the invitation code, we're done. (The controller will say
            // the code is invalid, but we don't want to display an error in this case.)
            if (signUpCode.stringValue.isEmpty) {
                return;
            }

            if (error) {
                NSString* msg = (error.code == PBException_TypeNotFound)
                                ? @"Invitation code not valid."
                                : [NSString stringWithFormat:TR(@"Couldn't verify invitation code (%@)", nil),
                                        error.localizedDescription];

                [status setError:msg];
                [self setStatusImage:icStatusImg successful:NO];
            } else {
                [status clear];
                [self setStatusImage:icStatusImg successful:YES];
                self.isSignUpCodeValid = YES;
                emailAddress.stringValue = reply.email;
                if (!reply.email.isEmpty) {
                    [emailAddress setEnabled:NO];
                }
            }
        }];
}

/**
* Validates that both password match
*/
- (IBAction)onPasswordsChanged:(id)sender
{
    [self setPasswordsMatch:NO];

    NSString* p1 = password.stringValue;
    NSString* p2 = password2.stringValue;

    // If both fields are equal and non-empty, mark as ok
    if (!p1.isEmpty && [p1 isEqual:p2]) {
        [status clear];
        [self setStatusImage:pwStatusImg successful:YES];
        [self setPasswordsMatch:YES];
        return;
    }

    // If one field is empty, do not show errors (user isn't done editing)
    if (p1.isEmpty || p2.isEmpty) {
        [status clear];
        [pwStatusImg setHidden:YES];
        return;
    }

    // Otherwise, display error
    [status setError:TR(@"Passwords don't match.", @"Setup dialog status message")];
    [self setStatusImage:pwStatusImg successful:NO];
}

/**
* Called continuously every time a text field is updated
*/
- (void)controlTextDidChange:(NSNotification*)notification
{
    NSTextField * changedField = notification.object;

    // Get the other password field (the one the user is _not_ editing)
    NSTextField* otherPasswordField = nil;
    if (changedField == password)  otherPasswordField = password2;
    if (changedField == password2) otherPasswordField = password;

    // Do not validate the passwords if the edited field is shorter than the other field
    // (the user isn't done re-typing his password)
    if (otherPasswordField) {
        if (changedField.stringValue.length >= otherPasswordField.stringValue.length) {
            [self onPasswordsChanged:changedField];
        } else {
            [pwStatusImg setHidden:YES];
            [status clear];
        }
    } else {
        // The notification didn't concern any of our password field.
        // Forward it to the designated action selector
        if (changedField.target == self && [self respondsToSelector:changedField.action]) {
            [self performSelector:changedField.action withObject:changedField];
        }
    }
}

- (void)computeFinishButtonState
{
    BOOL state = (self.isSignUpCodeValid
            && !firstName.stringValue.isEmpty
            && !lastName.stringValue.isEmpty
            && !emailAddress.stringValue.isEmpty
            && self.passwordsMatch
            && !self.setupInProgress
    );

    [finishButton setEnabled:state];
    [finishButton setKeyEquivalent:@"\r"];
}

- (void)setStatusImage:(NSImageView*)imgView successful:(BOOL)success
{
    imgView.image = [NSImage imageNamed: (success) ? @"tick" : @"exclamation.png"];
    [imgView setHidden:NO];
}

@end