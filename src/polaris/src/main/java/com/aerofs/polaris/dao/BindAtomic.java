package com.aerofs.polaris.dao;

import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@BindingAnnotation(BindAtomic.BindAtomicFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface BindAtomic {

    final class BindAtomicFactory implements BinderFactory {

        @SuppressWarnings("ConstantConditions")
        @Override
        public Binder<BindAtomic, Atomic> build(Annotation annotation) {
            return (q, bind, arg) -> {
                Object id = null;
                Object index = null;
                Object total = null;

                if (arg != null) {
                    id = arg.id;
                    index = arg.incrementAndGet();
                    total = arg.total;
                }

                q.bind("atomic_operation_id", id);
                q.bind("atomic_operation_index", index);
                q.bind("atomic_operation_total", total);
            };
        }
    }
}
