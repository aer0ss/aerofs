#import <objc/runtime.h>
#import "AeroUtils.h"


@implementation AeroUtils

+ (BOOL)swizzleClassMethod:(SEL)targetSel fromClass:(Class)targetClass
                withMethod:(SEL)newSel fromClass:(Class)otherClass
{
    return [self swizzleInstanceMethod:targetSel fromClass:object_getClass(targetClass)
            withMethod:newSel fromClass:object_getClass(otherClass)];
}

+ (BOOL)swizzleInstanceMethod:(SEL)targetSel fromClass:(Class)targetClass
                   withMethod:(SEL)newSel fromClass:(Class)otherClass
{
    // Note: this method is based on https://github.com/rentzsch/jrswizzle

    // Make sure both methods exist in their respective classes.
    Method origMethod = class_getInstanceMethod(targetClass, targetSel);
    Method newMethod = class_getInstanceMethod(otherClass, newSel);

    if (!origMethod || !newMethod) {
        NSLog(@"AeroFS: method %@ not found in class %@",
                NSStringFromSelector(origMethod ? newSel : targetSel),
                [(origMethod ? otherClass : targetClass) className]);
        return NO;
    }

    // Add the target method to the target class. This is important if the target method happens to
    // be defined on a super class. No-op if the target method is already defined in the target
    // class.
    class_addMethod(targetClass,
            targetSel,
            class_getMethodImplementation(targetClass, targetSel),
            method_getTypeEncoding(origMethod));

    // Add the new method to the target class.
    class_addMethod(targetClass,
            newSel,
            class_getMethodImplementation(otherClass, newSel),
            method_getTypeEncoding(newMethod));

    // Swizzle them!
    method_exchangeImplementations(
            class_getInstanceMethod(targetClass, targetSel),
            class_getInstanceMethod(targetClass, newSel));

    return YES;
}

@end

/**
* Replace the implementation of a method in a given class with a new one, and return the old
* implementation.
* If the method does not exist, adds the method to the class and returns nil.
*/
IMP replace_method(Class cls, SEL sel, IMP imp)
{
    Method origMethod = class_getInstanceMethod(cls, sel);
    return class_replaceMethod(cls, sel, imp, method_getTypeEncoding(origMethod));
}
