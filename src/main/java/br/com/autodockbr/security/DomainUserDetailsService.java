package br.com.autodockbr.security;

import br.com.autodockbr.domain.User;
import br.com.autodockbr.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("userDetailsService")
public class DomainUserDetailsService implements UserDetailsService {

    private final Logger log = LoggerFactory.getLogger(DomainUserDetailsService.class);

    private final UserRepository userRepository;

    public DomainUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(final String email) {
        log.debug("Authenticating by email {}", email);

        String lowercaseEmail = email.toLowerCase(Locale.ENGLISH);

        return userRepository
            .findOneByEmailIgnoreCase(lowercaseEmail)
            .map(user -> createSpringSecurityUser(lowercaseEmail, user))
            .orElseThrow(() -> new UsernameNotFoundException("User with email " + lowercaseEmail + " was not found in the database"));
    }

    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String lowercaseEmail, User user) {
        if (!user.isActivated()) {
            throw new UserNotActivatedException("User " + lowercaseEmail + " was not activated");
        }
        List<GrantedAuthority> grantedAuthorities = Collections.emptyList();
        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), grantedAuthorities);
    }
}
