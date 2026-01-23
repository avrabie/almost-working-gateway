package ai.almostworking.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecConfig {
    private static final Logger logger = LoggerFactory.getLogger(GatewaySecConfig.class);

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityContextRepository(new WebSessionServerSecurityContextRepository())
                .authorizeExchange(exchange -> exchange
                                .pathMatchers("/greetings").permitAll()
                                .pathMatchers("/hello").hasAnyRole("manage-account", "view-profile")
                                .anyExchange().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutHandler(new DelegatingServerLogoutHandler(
                                new WebSessionServerLogoutHandler(),
                                new SecurityContextServerLogoutHandler()
                        ))
                )
//                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("ALMOST_WORKING_SESSION");
        return resolver;
    }


    /**
     * Extracts authorities from OIDC user claims
     */
//    private Collection<GrantedAuthority> extractAuthorities(OidcUser oidcUser) {
//        return extractAuthorities(oidcUser.getClaims());
//    }

    /**
     * Extracts authorities from a map of claims
     */
    private Collection<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
        if (realmAccess != null) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) {
                roles.forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
                );
            }
        }
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> account =
                    (Map<String, Object>) resourceAccess.get("account");
            if (account != null) {
                List<String> roles =
                        (List<String>) account.get("roles");
                if (roles != null) {
                    roles.forEach(role ->
                            authorities.add(
                                    new SimpleGrantedAuthority("ROLE_" + role)
                            )
                    );
                }
            }
        }
        return authorities;
    }

    // and this is how you get roles from the OIDC access token
    @Bean
    public ReactiveOAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(ReactiveJwtDecoder jwtDecoder) {
        final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();

        return (userRequest) -> delegate.loadUser(userRequest)
                .flatMap(oidcUser -> {
                    String accessTokenValue = userRequest.getAccessToken().getTokenValue();
                    return jwtDecoder.decode(accessTokenValue)
                            .map(jwt -> {
                                Collection<GrantedAuthority> authorities = new ArrayList<>(oidcUser.getAuthorities());
                                // Extract from ID Token
                                authorities.addAll(extractAuthorities(oidcUser.getClaims()));
                                // Extract from Access Token
                                authorities.addAll(extractAuthorities(jwt.getClaims()));

                                // Remove duplicates
                                List<GrantedAuthority> uniqueAuthorities = authorities.stream()
                                        .distinct()
                                        .collect(Collectors.toList());

                                logger.info("Final authorities for {}: {}", oidcUser.getName(), uniqueAuthorities);
                                return (OidcUser) new DefaultOidcUser(uniqueAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
                            })
                            .onErrorResume(e -> {
                                logger.error("Failed to decode access token", e);
                                return Mono.just(oidcUser);
                            });
                });
    }



    // this Bean is for Microservices wanting to go through this Gateway
    @Bean
    ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                if (roles != null) {
                    roles.forEach(role ->
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                }
            }
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                Map<String, Object> account =
                        (Map<String, Object>) resourceAccess.get("account");
                if (account != null) {
                    List<String> roles =
                            (List<String>) account.get("roles");
                    if (roles != null) {
                        roles.forEach(role ->
                                authorities.add(
                                        new SimpleGrantedAuthority("ROLE_" + role)
                                )
                        );
                    }

                }
            }

            return Flux.fromIterable(authorities);
        });
        return converter;
    }


}
