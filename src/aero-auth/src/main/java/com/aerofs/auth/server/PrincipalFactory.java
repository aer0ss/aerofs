package com.aerofs.auth.server;

import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Generic implementation of an HK2 {@code Factory} that extracts
 * {@link AeroPrincipal} or subclasses of {@code AeroPrincipal}
 * from the request {@link SecurityContext} and injects them into
 * {@link javax.ws.rs.core.Context}-annotated JAX-RS objects and methods.
 * <br>
 * Use as follows:
 * <pre>
 * &#64Provider
 * public final class MySpecialPrincipalBinder extends AbstractBinder {
 *
 *     private static final class ActualPrincipalFactory extends PrincipalFactory&lt;MySpecialPrincipal&gt; {
 *
 *         &#64;Inject
 *         public ActualPrincipalFactory(SecurityContext securityContext) {
 *             super(securityContext);
 *         }
 *     }
 *
 *     &#64;Override
 *     protected void configure() {
 *         // we create a NEW instance of the factory every time
 *         // so that the SecurityContext associated with the CURRENT
 *         // request can be injected into the factory
 *         bindFactory(ActualPrincipalFactory.class).to(MySpecialPrincipal.class);
 *     }
 * }
 * </pre>
 * An instance of this class should <strong>only</strong> be used
 * within an HK2 {@link org.glassfish.hk2.utilities.binding.AbstractBinder}
 * that is added as a JAX-RS {@link javax.ws.rs.ext.Provider} to a
 * Jersey application.
 * <br>
 * This implementation depends on the {@link SecurityContext} injector
 * defined by Jersey and which is automatically added to Jersey applications.
 *
 * @param <T> subclass of {@link AeroPrincipal} that should be extracted from the request {@link SecurityContext}
 */
public abstract class PrincipalFactory<T extends AeroPrincipal> implements Factory<T> {

    private final SecurityContext securityContext;

    /**
     * Constructor.
     *
     * @param securityContext {@code SecurityContext} associated with this request
     */
    @Inject
    public PrincipalFactory(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    /**
     * Return instance of {@link Principal} of type {@code T}.
     *
     * @return null if the {@code SecurityContext} does not contain a {@code Principal},
     * valid instance of {@link AeroPrincipal} of type {@code T} otherwise
     * @throws IllegalArgumentException if the {@code SecurityContext} contains
     * a {@code Principal} that is <strong>not</strong> of type {@code T}
     */
    @SuppressWarnings("unchecked")
    @Override
    public T provide() {
        Principal principal = securityContext.getUserPrincipal();

        if (principal == null) {
            return null;
        }

        try {
            return (T) principal;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(String.format("invalid principal type: %s", principal.getClass().getSimpleName()));
        }
    }

    @Override
    public void dispose(T instance) {
        // nothing to do here
    }
}
