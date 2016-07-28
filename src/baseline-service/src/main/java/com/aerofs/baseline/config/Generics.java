/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.config;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

// XXX (AG): the bottom methods were pulled directly from DropWizard
// The following code is covered by ASL 2.0
// It is based on the principles in:
//   * http://gafter.blogspot.com/2006/12/super-type-tokens.html
//   * http://www.artima.com/weblogs/viewpost.jsp?thread=208860
@SuppressWarnings("unchecked")
@ThreadSafe
abstract class Generics {

    /**
     * Finds the objectType parameter for the given class which is assignable to the bound class.
     *
     * @param klass    a parameterized class
     * @param bound    the objectType bound
     * @param <T>      the objectType bound
     * @return the class's objectType parameter
     */
    static <T> Class<T> getTypeParameter(Class<?> klass, Class<? super T> bound) {
        Type t = Preconditions.checkNotNull(klass, "cannot get type parameter for null object");
        while (t instanceof Class<?>) {
            t = ((Class<?>) t).getGenericSuperclass();
        }

        /* This is not guaranteed to work for all cases with convoluted piping
         * of objectType parameters: but it can at least resolve straight-forward
         * extension with single objectType parameter (as per [Issue-89]).
         * And when it fails to do that, will indicate with specific exception.
         */
        if (t instanceof ParameterizedType) {
            // should typically have one of objectType parameters (first one) that matches:
            for (Type param : ((ParameterizedType) t).getActualTypeArguments()) {
                if (param instanceof Class<?>) {
                    final Class<T> cls = determineClass(bound, param);
                    if (cls != null) { return cls; }
                }
                else if (param instanceof TypeVariable) {
                    for (Type paramBound : ((TypeVariable<?>) param).getBounds()) {
                        if (paramBound instanceof Class<?>) {
                            final Class<T> cls = determineClass(bound, paramBound);
                            if (cls != null) { return cls; }
                        }
                    }
                }
            }
        }

        throw new IllegalStateException("Cannot figure out type parameterization for " + klass.getName());
    }

    @Nullable
    private static <T> Class<T> determineClass(Class<? super T> bound, Type candidate) {
        if (candidate instanceof Class<?>) {
            final Class<?> cls = (Class<?>) candidate;
            if (bound.isAssignableFrom(cls)) {
                return (Class<T>) cls;
            }
        }

        return null;
    }

    private Generics() {
        // to prevent instantiation by subclasses
    }
}
