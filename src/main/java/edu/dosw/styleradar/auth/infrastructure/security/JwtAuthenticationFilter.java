package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "StringRedisTemplate es una dependencia administrada por Spring e inyectada por constructor.")
    public JwtAuthenticationFilter(
            JwtService jwtService,
            StringRedisTemplate redisTemplate) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtService.validarToken(token);

            Long usuarioId = Long.valueOf(claims.getSubject());
            long fechaEmision = claims.getIssuedAt().getTime();

            String fechaRevocacion = redisTemplate.opsForValue()
                    .get("usuario:revocado:" + usuarioId);

            if (fechaRevocacion != null
                    && fechaEmision <= Long.parseLong(fechaRevocacion)) {
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "El token ha sido revocado");
                return;
            }

            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            if (roles != null) {
                for (String rol : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
                }
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token inválido o expirado");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
