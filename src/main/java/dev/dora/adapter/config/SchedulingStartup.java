/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fails application startup when {@code scheduling.*} cannot be turned into valid rules.
 *
 * @author Rodrigo Prestes Machado
 */
@Startup
@ApplicationScoped
public class SchedulingStartup {

    /**
     * Validates the scheduling configuration as soon as the bean is created.
     *
     * @param config typed scheduling configuration
     */
    @Inject
    public SchedulingStartup(SchedulingConfig config) {
        SchedulingRulesFactory.create(config);
    }
}
