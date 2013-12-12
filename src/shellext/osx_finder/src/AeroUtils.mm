#import <objc/runtime.h>
#import "AeroUtils.h"

//private:
static BOOL swizzleMethod(SEL m1, Class c1, SEL m2, Class c2, BOOL isInstanceMethod);

@implementation AeroUtils

+(BOOL) swizzleClassMethod:(SEL)m1 fromClass:(Class)c1 withMethod:(SEL)m2 fromClass:(Class)c2
{
    return swizzleMethod(m1, c1, m2, c2, YES);
}

+(BOOL) swizzleInstanceMethod:(SEL)m1 fromClass:(Class)c1 withMethod:(SEL)m2 fromClass:(Class)c2
{
    return swizzleMethod(m1, c1, m2, c2, NO);
}

@end

BOOL swizzleMethod(SEL origSel, Class targetClass, SEL newSel, Class ourClass, BOOL isClassMethod)
{
    // 1. Get the implementation and signature of our method
    Method ourMeth = isClassMethod ? class_getClassMethod(ourClass, newSel) : class_getInstanceMethod(ourClass, newSel);
    IMP implementation =  method_getImplementation(ourMeth);
    const char* signature =  method_getTypeEncoding(ourMeth);

    // 2. Add a new method in the target class with the same implementation and signature
    // If it's a class method, we must add the method to the meta class of the target
    Class targetOrMetaClass = isClassMethod? objc_getMetaClass(class_getName(targetClass)) : targetClass;
    BOOL success = class_addMethod(targetOrMetaClass, newSel, implementation, signature);
    if (!success) {
        NSLog(@"AeroFS: Adding method failed"); //TODO: Give more information: class name, method name
        return NO;
    }

    // 3. Swizzle the old and the new methods in the target class
	Method meth;
	Method newMeth;
    if (isClassMethod) {
        meth = class_getClassMethod(targetClass, origSel);
        newMeth = class_getClassMethod(targetClass, newSel);
    } else {
        meth = class_getInstanceMethod(targetClass, origSel);
        newMeth = class_getInstanceMethod(targetClass, newSel);
    }

    if (meth && newMeth) {
        method_exchangeImplementations(meth, newMeth);
        return YES;
    } else {
        NSLog(@"AeroFS: Method swizzling failed %@ %@",
                NSStringFromClass(targetClass),
                NSStringFromSelector(origSel));
        return NO;
    }
}
