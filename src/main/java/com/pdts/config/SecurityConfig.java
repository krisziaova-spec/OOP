package com.pdts.config;

import com.pdts.model.User;
import com.pdts.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserRepository userRepo;
    private final PdtsProperties pdtsProperties;

    public SecurityConfig(UserRepository userRepo, PdtsProperties pdtsProperties) {
        this.userRepo = userRepo;
        this.pdtsProperties = pdtsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled for school/demo HTML form simplicity.
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/assets/**",
                                "/webjars/**",
                                "/favicon.ico"
                        ).permitAll()

                        .requestMatchers(
                                "/",
                                "/login",
                                "/forgot-password",
                                "/login-error",
                                "/error",
                                "/access-denied",
                                "/portal",
                                "/portal/**",
                                "/public",
                                "/track",
                                "/api/portal/**"
                        ).permitAll()

                        // Logged-in user photo upload/viewing is available to all authenticated staff.
                        .requestMatchers(
                                "/profile/upload-photo",
                                "/uploads/user-photos/**",
                                "/uploads/requirements/**"
                        ).authenticated()

                        // Operational review actions are available to Admin and Head Admission only.
                        .requestMatchers(
                                "/api/rejection-reasons/**",
                                "/api/requirements/*/receive",
                                "/api/requirements/*/reject",
                                "/api/requirements/*/resubmit",
                                "/requirements/*/status"
                        ).hasAnyRole("HEAD_ADMISSION", "ADMIN")

                        // Head Admission is the only role allowed to manage system-wide controls.
                        .requestMatchers(
                                "/users/**",
                                "/settings/**",
                                "/logs/**"
                        ).hasRole("HEAD_ADMISSION")

                        // Admin is office staff with operational access, but no user/settings/logs control.
                        .requestMatchers(
                                "/email-notifications/**",
                                "/reports/**"
                        ).hasAnyRole("HEAD_ADMISSION", "ADMIN")

                        // Admission Personnel / Student Assistants retain limited working access.
                        .requestMatchers(
                                "/dashboard",
                                "/applicants/**",
                                "/requirements/**",
                                "/tracking-lookup/**"
                        ).hasAnyRole("HEAD_ADMISSION", "ADMIN", "ADMISSION_PERSONNEL")

                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied")
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")

                        .successHandler((request, response, authentication) -> {
                            System.out.println("[PDTS LOGIN SUCCESS] username="
                                    + authentication.getName());
                            response.sendRedirect("/dashboard");
                        })

                        .failureHandler((request, response, exception) -> {
                            String submittedUsername = request.getParameter("username");

                            System.out.println("[PDTS LOGIN FAILURE] submitted_username="
                                    + submittedUsername);
                            System.out.println("[PDTS LOGIN FAILURE] exception_class="
                                    + exception.getClass().getName());
                            System.out.println("[PDTS LOGIN FAILURE] exception_message="
                                    + exception.getMessage());

                            response.sendRedirect("/login?error=true");
                        })

                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }

  @Bean
public UserDetailsService userDetailsService() {
    return username -> {
        String loginUsername = username == null ? "" : username.trim();

        System.out.println("[PDTS USER LOOKUP] received_username=" + username);
        System.out.println("[PDTS USER LOOKUP] trimmed_username=" + loginUsername);

        User user = userRepo.findByUsernameIgnoreCase(loginUsername)
                .orElseThrow(() -> {
                    System.out.println("[PDTS USER LOOKUP] RESULT=NOT_FOUND username="
                            + loginUsername);
                    return new UsernameNotFoundException("User not found: " + loginUsername);
                });

        System.out.println("[PDTS USER LOOKUP] RESULT=FOUND username="
                + user.getUsername());

        System.out.println("[PDTS USER LOOKUP] active="
                + user.getIsActive());

        String savedPassword = user.getPasswordHash() == null
                ? ""
                : user.getPasswordHash().trim();

        String passwordType;

        if (savedPassword.startsWith("{noop}")) {
            passwordType = "NOOP";
        } else if (savedPassword.startsWith("{bcrypt}")) {
            passwordType = "BCRYPT_WITH_PREFIX";
        } else if (savedPassword.startsWith("$2a$")
                || savedPassword.startsWith("$2b$")
                || savedPassword.startsWith("$2y$")) {
            passwordType = "BCRYPT";
        } else if (savedPassword.isBlank()) {
            passwordType = "BLANK";
        } else {
            passwordType = "PLAIN_TEXT_OR_UNKNOWN";
        }

        System.out.println("[PDTS USER LOOKUP] password_type=" + passwordType);

        if (user.getIsActive() == null || user.getIsActive() == 0) {
            System.out.println("[PDTS USER LOOKUP] RESULT=DISABLED username="
                    + loginUsername);
            throw new DisabledException("Account is deactivated.");
        }

        String roleName = user.getRole() != null
                ? user.getRole().getRoleName()
                : "USER";

        String authority = "ROLE_" + roleName.toUpperCase().replace(" ", "_");

        System.out.println("[PDTS USER LOOKUP] role=" + roleName);
        System.out.println("[PDTS USER LOOKUP] authority=" + authority);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                savedPassword,
                List.of(new SimpleGrantedAuthority(authority))
        );
    };
}

  @Bean
public PasswordEncoder passwordEncoder() {
    int strength = pdtsProperties.getBcryptStrength() > 0
            ? pdtsProperties.getBcryptStrength()
            : 10;

    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(strength);

    return new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return bcrypt.encode(rawPassword == null ? "" : rawPassword.toString());
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            String typed = rawPassword == null
                    ? ""
                    : rawPassword.toString().trim();

            String saved = encodedPassword == null
                    ? ""
                    : encodedPassword.trim();

            System.out.println("[PDTS PASSWORD CHECK] typed_length="
                    + typed.length());

            if (saved.startsWith("{noop}")) {
                boolean result = typed.equals(saved.substring(6));
                System.out.println("[PDTS PASSWORD CHECK] type=NOOP result="
                        + result);
                return result;
            }

            if (saved.startsWith("{bcrypt}")) {
                String bcryptHash = saved.substring("{bcrypt}".length());
                boolean result = bcrypt.matches(typed, bcryptHash);
                System.out.println("[PDTS PASSWORD CHECK] type=BCRYPT_WITH_PREFIX result="
                        + result);
                return result;
            }

            if (saved.startsWith("$2a$")
                    || saved.startsWith("$2b$")
                    || saved.startsWith("$2y$")) {
                boolean result = bcrypt.matches(typed, saved);
                System.out.println("[PDTS PASSWORD CHECK] type=BCRYPT result="
                        + result);
                return result;
            }

            boolean result = typed.equals(saved);
            System.out.println("[PDTS PASSWORD CHECK] type=PLAIN_OR_UNKNOWN result="
                    + result);
            return result;
        }
    };
}

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
