package com.hireflow.application.transition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free {@link TransitionValidator} into the Spring context as a bean,
 * without putting any Spring annotations on the validator class itself.
 */
@Configuration
public class TransitionValidatorConfig {

    @Bean
    public TransitionValidator transitionValidator() {
        return new TransitionValidator();
    }
}
