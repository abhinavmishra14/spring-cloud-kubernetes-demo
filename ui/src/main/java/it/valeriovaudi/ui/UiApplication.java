package it.valeriovaudi.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class UiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiApplication.class, args);
    }

}

class UserIntrospectorOidcUserService implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;

    public UserIntrospectorOidcUserService(OidcUserService delegate) {
        this.delegate = delegate;
    }



    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        return Mono.empty();
    }

    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        Collection<GrantedAuthority> mappedAuthorities = authoritiesFor(oidcUser);

        return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }


    private Set<GrantedAuthority> authoritiesFor(OidcUser user) {
        List<String> authorities = authoritiesFrom(user);
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .map(authority -> new OidcUserAuthority(authority.getAuthority(), user.getIdToken(), user.getUserInfo()))
                .collect(Collectors.toSet());
    }

    private List<String> authoritiesFrom(OidcUser oidcUser) {
        List<String> authoritiesClaim = (List<String>) oidcUser.getClaimAsMap("realm_access").get("roles");
        return Optional.ofNullable(authoritiesClaim).orElse(Collections.emptyList());
    }
}

@EnableWebFluxSecurity
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public ReactiveOAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();


        return (userRequest) -> {
            // Delegate to the default implementation for loading a user
            return delegate.loadUser(userRequest)
                    .flatMap((oidcUser) -> {
                        List<String> authorities = (List<String>) oidcUser.getClaimAsMap("realm_access").get("roles");
                        Set<OidcUserAuthority> oidcAuthorities = authorities.stream()
                                .map(SimpleGrantedAuthority::new)
                                .map(authority -> new OidcUserAuthority(authority.getAuthority(), oidcUser.getIdToken(), oidcUser.getUserInfo()))
                                .collect(Collectors.toSet());

                        return Mono.just(new DefaultOidcUser(oidcAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo()));
                    });
        };


    }

    @Bean
    public SecurityWebFilterChain defaultSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.headers(configurer -> configurer.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable));

        http.logout(logoutSpec -> {
            logoutSpec.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository));
        });


        http.oauth2Login(Customizer.withDefaults());

        http.authorizeExchange(
                auth ->
                        auth
                                .pathMatchers("/index.html").hasAuthority("ADMIN")
                                .pathMatchers("/messages.html").hasAuthority("USER")
                                .anyExchange().permitAll()
        );


        return http.build();
    }

    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);

        // Sets the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");

        return oidcLogoutSuccessHandler;
    }
}
