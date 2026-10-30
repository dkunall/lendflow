package com.lendflow.shared.web;

import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.domain.Actor.Role;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers take an {@link Actor} instead of depending on Spring Security types.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.getParameterType().equals(Actor.class)) {
            return true;
        }
        return parameter.getParameterType().equals(Optional.class)
            && parameter.nestedIfOptional().getNestedParameterType().equals(Actor.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        Optional<Actor> actor = current();
        if (parameter.getParameterType().equals(Optional.class)) {
            return actor;
        }
        return actor.orElseThrow(() -> new IllegalStateException("Actor requested on a request without a JWT"));
    }

    static Optional<Actor> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken) || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : auth.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "SCOPE_lendflow.applicant" -> roles.add(Role.APPLICANT);
                case "SCOPE_lendflow.underwriter" -> roles.add(Role.UNDERWRITER);
                case "SCOPE_lendflow.credit_manager" -> roles.add(Role.CREDIT_MANAGER);
                case "SCOPE_lendflow.credit_head" -> roles.add(Role.CREDIT_HEAD);
                case "SCOPE_lendflow.policy" -> roles.add(Role.POLICY_ADMIN);
                case "SCOPE_lendflow.ops" -> roles.add(Role.OPS);
                default -> { }
            }
        }
        return Optional.of(new Actor(auth.getName(), roles));
    }
}
