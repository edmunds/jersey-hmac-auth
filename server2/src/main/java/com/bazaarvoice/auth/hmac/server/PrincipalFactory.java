package com.bazaarvoice.auth.hmac.server;

import static com.bazaarvoice.auth.hmac.common.Credentials.builder;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.apache.commons.lang.Validate.notNull;

import java.net.URI;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.glassfish.hk2.api.Factory;
import org.glassfish.jersey.server.ContainerRequest;

import com.bazaarvoice.auth.hmac.common.Credentials.CredentialsBuilder;
import com.bazaarvoice.auth.hmac.common.Version;

/**
 * {@link Factory} for creating a principal wherever it is required for a request.
 *
 * @param <P> The type of principal
 * @see Authenticator
 */
public class PrincipalFactory<P> implements Factory<P> {

    private final Authenticator<? extends P> authenticator;
    private final Provider<? extends ContainerRequest> requestProvider;

    /**
     * @param authenticator the application's credential authenticator (required)
     * @param requestProvider object that provides access to the active request (required)
     */
    @Inject
    public PrincipalFactory(final Authenticator<P> authenticator,
            final Provider<ContainerRequest> requestProvider) {
        // we could technically declare the dependency as Authenticator<? extends P>, but that complicates HK2
        // dependency-injection
        notNull(authenticator, "authenticator cannot be null");
        this.authenticator = authenticator;
        this.requestProvider = requestProvider;
    }

    public P provide() {
        final ContainerRequest request = getRequestProvider().get();
        final UriInfo uriInfo = request.getUriInfo();
        final URI requestUri = uriInfo.getRequestUri();

        final MultivaluedMap<? super String, ? extends String> queryParameters = uriInfo
                .getQueryParameters();
        final List<? extends String> apiKeys = queryParameters.get("apiKey");
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new BadRequestException("apiKey is required");
        }

        final CredentialsBuilder builder = builder();
        builder.withApiKey(!apiKeys.isEmpty() ? apiKeys.get(0) : null);
        builder.withSignature(request.getHeaderString("X-Auth-Signature"));
        builder.withTimestamp(request.getHeaderString("X-Auth-Timestamp"));
        builder.withVersion(
                Version.fromValue(request.getHeaderString("X-Auth-Version")));
        builder.withMethod(request.getMethod());
        builder.withPath(requestUri.getPath() + "?" + requestUri.getQuery());

        final P retval = getAuthenticator().authenticate(builder.build());
        if (retval == null) {
            throw new NotAuthorizedException(status(UNAUTHORIZED).build());
        }
        return retval;
    }

    public void dispose(final P instance) {
    }

    protected Authenticator<? extends P> getAuthenticator() {
        return authenticator;
    }

    protected Provider<? extends ContainerRequest> getRequestProvider() {
        return requestProvider;
    }

}