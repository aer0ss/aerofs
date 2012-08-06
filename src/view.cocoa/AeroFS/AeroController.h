#import <Cocoa/Cocoa.h>
#import <JavaVM/jni.h>
#import "../gen/Controller.pb.h"

// Those are the keys we define for error-handling
extern NSString* const StackTraceKey;
extern NSString* const ControllerErrorDomain;

/**
* This is the class that manages the Java controller
* Main responsabilities:
*   - Create the JVM and load the controller class
*   - Provide an RPC interface to talk with the controller
*/
@interface AeroController : NSObject<ServiceStubDelegate> {
@private
    JavaVM* jvm;
    jmethodID controllerSendRequestMethod;
    NSCondition* conditionControllerReady;
    ControllerServiceStub* stub;
}

+ (AeroController*) instance;
- (ControllerServiceStub*) stub;
+ (ControllerServiceStub*) stub;
- (void)sendBytes:(NSData*)bytes param1:(id)param1 param2:(id)param2;
- (NSError*)decodeError:(PBException*)error;

@end
