package com.miranda.luna.journal.infrastructure.security;

import com.miranda.luna.journal.domain.model.AuthenticatedOperator;

import java.util.Optional;

public interface LciAuthClientPort {
    Optional<AuthenticatedOperator> introspect(String token);
}
