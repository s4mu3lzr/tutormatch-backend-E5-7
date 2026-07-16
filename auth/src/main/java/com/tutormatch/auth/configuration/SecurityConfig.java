package com.tutormatch.auth.configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.tutormatch.auth.security.UsuarioPrincipal;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Configura la seguridad para los endpoints de OAuth2
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .oauth2AuthorizationServer(authorizationServer -> {
                    // Configura automáticamente qué rutas pertenecen al Auth Server
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    // Habilita OpenID Connect (para que el Auth Server pueda manejar solicitudes de
                    // servidores externos)
                    authorizationServer
                            .authorizationEndpoint(authEndpoint -> authEndpoint.consentPage("/oauth2/consent"))
                            .oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }

    // Indica que cualquier petición (que no sea de los endpoints de OAuth2)
    // requiere que el usuario esté autenticado
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/.well-known/**", "/favicon.ico", "/error", "/login", "/css/**").permitAll()
                        .anyRequest().authenticated())
                // Habilita el formulario de inicio de sesión por defecto de Spring Security
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll());
        return http.build();
    }

    // Define quiénes son las aplicaciones clientes que pueden solicitar tokens.
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("frontend-client")
                // Cliente Público (NONE) ya que Angular no puede guardar secretos
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                // Indica que el cliente puede solicitar tokens usando el flujo de autorización
                // y el flujo de refresh token
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // Redirección hacia tu frontend en Angular (ajustaremos este puerto después si
                // es necesario)
                .redirectUri("http://localhost:4200/") // Redirección tras login
                .postLogoutRedirectUri("http://localhost:4200/") // Redirección tras logout
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(true)
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    // Inyecta Roles en el Access Token y Perfil en el ID Token
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return (context) -> {
            Authentication principal = context.getPrincipal();

            // Validamos que el usuario es el que se está logueando
            if (principal.getPrincipal() instanceof UsuarioPrincipal) {
                UsuarioPrincipal usuarioLogueado = (UsuarioPrincipal) principal.getPrincipal();

                // 1. Access Token: Para los Microservicios
                if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                    Set<String> roles = principal.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toSet());
                    context.getClaims().claim("roles", roles);
                    context.getClaims().claim("usuario_id", usuarioLogueado.getId().toString());
                    // Nombre del usuario en el access token para que ms-core lo use
                    // al crear sesiones (evita llamada HTTP a ms-usuarios)
                    context.getClaims().claim("nombre", usuarioLogueado.getNombre());
                }

                // 2. ID Token: Para el frontend en Angular
                if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                    context.getClaims().claim("nombre", usuarioLogueado.getNombre());
                    context.getClaims().claim("email", usuarioLogueado.getUsername());
                }
            }
        };
    }

    // Codificador de contraseñas para leer el BCrypt de Supabase
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Configura la clave pública y privada que el Auth Server usará para firmar los
    // tokens JWT y que el Resource Server usará para verificar.
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // Centraliza la configuración de las rutas del servidor.
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    // Configura CORS para permitir solicitudes desde el frontend en Angular
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}