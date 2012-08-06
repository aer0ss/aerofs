
@interface AeroSetupStatusView : NSView

@property (assign) IBOutlet NSTextField* label;
@property (assign) IBOutlet NSProgressIndicator* progressIndicator;
@property (assign) IBOutlet NSImageView* image;

- (void)setError:(NSString*)msg;
- (void)setSuccess:(NSString*)msg;
- (void)setProgress:(NSString*)msg;
- (void)clear;

@end