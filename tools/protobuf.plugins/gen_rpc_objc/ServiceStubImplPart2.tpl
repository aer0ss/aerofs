    default: {
      NSAssert(false, @"Unknown reply");
      return;
    }
  }

  if (param2) {

    // standard selector / target callback

    id target = param2;
    SEL selector = (SEL) param1;

    // TODO: Assert that signature is not nil (ie: that the target does respond to that selector);
    NSMethodSignature* signature = [target methodSignatureForSelector:selector];
    NSInvocation* invocation = [NSInvocation invocationWithMethodSignature:signature];
    [invocation setTarget:target];
    [invocation setSelector:selector];
    if (error) {
      [invocation setArgument:&error atIndex:[signature numberOfArguments] - 1];
    } else {
      [invocation setArgument:&reply atIndex:2];
    }
    [invocation performSelectorOnMainThread:@selector(invoke) withObject:nil waitUntilDone:NO];

  } else {

    // block callback

    if (param1) {
      void(^block)(id reply, NSError* error) = param1;
      block(reply, error);
      [block release];
    }
  }
}
