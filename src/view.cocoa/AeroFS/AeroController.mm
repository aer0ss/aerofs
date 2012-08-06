#import "AeroController.h"
#import "AeroFSAppDelegate.h"
#import "launcher.lib/liblauncher.h"
#import "Controller.pb.h"

#define CONTROLLER_CLASS "com/aerofs/controller/ControllerProgram"

NSString* const StackTraceKey = @"StackTraceKey";
NSString* const ControllerErrorDomain = @"ControllerErrorDomain";

static NSString* const kController = @"controller";
static NSString* const kDefaultRtRoot = @"DEFAULT";

typedef struct {
    char* methodName;
    char* signature;
} JavaMethod;

JavaMethod const kControllerSendRequest  = {(char*)"onRequestFromView", (char*)"([BJJ)V"};
JavaMethod const kControllerReceiveReply = {(char*)"sendReplyToView", (char*)"([BJJ)V"};
JavaMethod const kControllerReceiveNotification = {(char*)"sendNotificationToView", (char*)"(I[B)V"};

@interface AeroController(Private)
-(void) jvmRun;
-(void) detachMainThread;
@end

void JNICALL onReplyReceived(JNIEnv* env, jclass cls, jbyteArray data, jlong param1, jlong param2);
void JNICALL onNotificationReceived(JNIEnv* env, jclass cls, jint type, jbyteArray byteArray);
NSData* newNSDataFromJByteArray(JNIEnv* env, jbyteArray byteArray);

@implementation AeroController

/**
* Returns the global singleton instance for this class
* Does not perform lazy initialazation. An instance of AeroController must have been
* previously initialized.
*/
+ (AeroController*) instance
{
    static dispatch_once_t pred;
    static AeroController* instance = nil;

    dispatch_once(&pred, ^{
        instance = [[AeroController alloc] init];
    });

    return instance;
}

/**
* Returns the service stub
*/
- (ControllerServiceStub*) stub
{
    return stub;
}

+ (ControllerServiceStub*) stub
{
    return [[AeroController instance] stub];
}

/**
* AeroController provides an interface to the Java controller.
* This is the main point of contact between Java and Objective-C code.
*
* Main tasks:
*   - Initialize the JVM, starts the ControllerProgram
*   - Implements the callbacks to receive and send RPC calls to the controller
*
* Note: currently, only the main thread is allowed to instantiate this class and call its methods.
* This is because before accessing the JVM, a thread has to associate itself with the JVM, and then
* detach itself from the JVM once it's done. Currently, we don't provide any methods to allow for
* other threads to do this.
*/
- (id)init
{
    self = [super init];
    if (self) {
        stub = [[ControllerServiceStub alloc] initWithDelegate:self];

        jvm = NULL;
        controllerSendRequestMethod = NULL;
        conditionControllerReady = [[NSCondition alloc] init];

        NSThread* myThread = [[[NSThread alloc] initWithTarget:self selector:@selector(jvmRun) object:nil] autorelease];
        [myThread start];

        // BLock the main thread until we get the pointer to the receiveMessageFromView method
        // From that point on, the controller should be ready for requests
        [conditionControllerReady lock];
        while(controllerSendRequestMethod == NULL) {
            [conditionControllerReady wait];
        }
        [conditionControllerReady unlock];
        [conditionControllerReady release];
        conditionControllerReady = nil;

        // Now that the JVM is created, attach the main thread to the JVM
        JNIEnv* env;
        jvm->AttachCurrentThread((void **)&env, NULL);
    }

    return self;
}

- (void)dealloc
{
    [stub release];
    [super dealloc];
}

/**
* This is the main JVM thread
* We refer to this as the Controller Thread
*/
- (void) jvmRun
{
    JNIEnv* env = NULL;
    char** args = NULL;

    @try {

        // get the command line
        NSMutableArray* arguments = [NSMutableArray arrayWithArray:
                [[NSProcessInfo processInfo] arguments]];

        // remove the first argument (path to the executable)
        [arguments removeObjectAtIndex:0];

        // Ensures the last argument is "gui"
        if (![[arguments lastObject] isEqualToString:kController]) {
            [arguments addObject:kController];
        }

        // Ensures the argument before last is rt root (or "DEFAULT")
        int beforeLast = [arguments count] - 2;
        if (beforeLast < 0 ||  ![[arguments objectAtIndex:beforeLast] characterAtIndex:0] == '-') {
            UInt pos = (beforeLast < 0) ? 0 : beforeLast;
            [arguments insertObject:kDefaultRtRoot atIndex:pos];
        }

        // Convert the NSArray to char**
        const int argc = [arguments count];
        args = new char*[argc + 1];
        for (UInt i = 0; i < argc; i++) {
            args[i] = (char*) [[arguments objectAtIndex:i] UTF8String];
        }
        args[argc] = NULL;

        NSString* appRoot = [AeroFSAppDelegate appRoot];
        char* errMsg = (char*)"";
        bool success = launcher_create_jvm([appRoot UTF8String], args, &jvm, &env, &errMsg);
        if (!success) {
            [NSException raise:@"Unable to create the JVM" format:@"Error message: %s", errMsg];
        }

        jclass cls = env->FindClass(CONTROLLER_CLASS);
        if (cls == NULL) {
            [NSException raise:@"Class not found" format:@"Could not find class %s", CONTROLLER_CLASS];
        }

        // Tell the JVM where our native methods are
        JNINativeMethod methods[2];

        methods[0].name = kControllerReceiveReply.methodName;
        methods[0].signature = kControllerReceiveReply.signature;
        methods[0].fnPtr = (void*) onReplyReceived;

        methods[1].name = kControllerReceiveNotification.methodName;
        methods[1].signature = kControllerReceiveNotification.signature;
        methods[1].fnPtr = (void*)onNotificationReceived;

        env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0]));

        controllerSendRequestMethod = env->GetStaticMethodID(cls, kControllerSendRequest.methodName,
                kControllerSendRequest.signature);
        if (controllerSendRequestMethod == NULL) {
            [NSException raise:@"MEthod not found" format:@"Could not find method %s in class %s",
                            kControllerSendRequest.methodName, CONTROLLER_CLASS];
        }

        // Signal the main thread that the controller is loaded
        [conditionControllerReady lock];
        [conditionControllerReady signal];
        [conditionControllerReady unlock];

    } @catch (NSException* e) {
        // TODO: Display error message to the user
        NSLog(@"Exception occured while trying to create JVM: %@\n\n", e);

        // Display Java exception if any
        if (env && env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }

        launcher_destroy_jvm(jvm);
        exit(1);

    }

    char* errMsg = (char*)"";
    int exitCode = launcher_launch(env, &errMsg);

    if (exitCode != 0) {
        NSLog(@"Controller exited with code: %i - message:%s", exitCode, errMsg);
    }
    if (env->ExceptionOccurred()) {
        env->ExceptionDescribe();
    }

    delete args; // FIXME (GS): if we delete the args BEFORE calling launcher_launch, we sometimes
                 // have an NPE because the launcher keeps a global ref to the args.
                 // This is a terribly bad idea. launcher_launch() should not keep the reference,
                 // and instead ask for args again.

    [self performSelectorOnMainThread:@selector(detachMainThread) withObject:nil waitUntilDone:NO];
    launcher_destroy_jvm(jvm);
}

/**
* This selector must be performed on the main thread, before destroying the JVM
*/
-(void) detachMainThread
{
    if (jvm) {
        jvm->DetachCurrentThread();
    }
}

/**
* ServiceStubDelegate
* Sends the bytes to the controller
* The reply will be received by onReplyReceived
* TODO: document attaching the current thread with the JVM
* This method is thread-safe
*/
- (void)sendBytes:(NSData*)msg param1:(id)param1 param2:(id)param2;
{
    // Try to get a pointer to the JNI
    // This will fail if the current thread hasn't been previously attached with: jvm->AttachCurrentThread
    JNIEnv* env = NULL;
    jint result = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        // TODO: better exception
        // TODO: document attach thread and this exception
        [NSException raise:@"Current thread not attached" format:@"Call attachCurrentThread"];
        return;
    }
    assert(result == JNI_OK);

    const char* data = (const char*) [msg bytes];
    const int length = [msg length];
    const jclass controllerClass = env->FindClass(CONTROLLER_CLASS);

    // I double dare you to call me with invalid arguments
    assert(env);
    assert(data);
    assert(length);
    assert(controllerClass);
    assert(controllerSendRequestMethod);

    jbyteArray jdata = env->NewByteArray(length);
    env->SetByteArrayRegion(jdata, 0, length, (const jbyte*)data);
    env->CallStaticVoidMethod(controllerClass, controllerSendRequestMethod, jdata, param1, param2);
    env->DeleteLocalRef(jdata);
}

/**
* ServiceStubDelegate
* Called by the reactor to decode an error reply from the controller into an NSError
*/
- (NSError*)decodeError:(PBException*)error
{
    NSLog(@"ERROR: %@", error);

    NSDictionary* info = [NSDictionary dictionaryWithObjectsAndKeys:
            error.localizedMessage, NSLocalizedDescriptionKey,
            error.stackTrace, StackTraceKey,
            nil];

    return [NSError errorWithDomain:ControllerErrorDomain code:[error type] userInfo:info];
}

/**
* Called by the controller with the byte array that replied to the previous request
* Thread context: controller thread
*/
void JNICALL onReplyReceived(JNIEnv* env, jclass cls, jbyteArray byteArray, jlong param1, jlong param2)
{
    NSData* reply = newNSDataFromJByteArray(env, byteArray);
    [[[AeroController instance] stub] onReplyReceived:reply param1:(id)param1 param2:(id)param2];
    [reply release];
}

/**
* Called by the controller to send us a notification
* Thread context: any thread
*/
void JNICALL onNotificationReceived(JNIEnv* env, jclass cls, jint type, jbyteArray byteArray)
{
    NSData* notifData = newNSDataFromJByteArray(env, byteArray);
    /* TODO:
        - check if anyone is listening for notifications of that type
        - dispatch notification
     */
    NSLog(@"Received notification of type %i from controller - ignored.", type);
    [notifData release];
}

///////////////////////////////////////////////////
// Helper methods
// (ie: no side-effects)
//////////////////////////////////////////////////

/**
* Creates a new NSData from a Java byte array
* You must release the returned NSData manually when you no longer need it.
*/
NSData* newNSDataFromJByteArray(JNIEnv* env, jbyteArray byteArray)
{
    assert(env);

    if (!byteArray) {
        return nil;
    }

    const char* bytes = (char*) env->GetByteArrayElements(byteArray, 0);
    const int length = env->GetArrayLength(byteArray);

    // NSData will copy the bytes to its own buffer
    NSData* result = [[NSData alloc] initWithBytes: bytes length: length];

    // So its safe to release the byte array now
    env->ReleaseByteArrayElements(byteArray, (jbyte*) bytes, JNI_ABORT);

    return result;
}

@end
