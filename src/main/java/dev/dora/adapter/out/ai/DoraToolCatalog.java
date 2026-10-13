/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.ai;

import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Supplier;

/**
 * Supplies the Dora tools to {@link DoraAgent}. Descriptions come from
 * {@code prompts/dora-tools.txt}, not from annotations on {@link DoraTools}.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class DoraToolCatalog implements Supplier<ToolProvider> {

    private final DoraToolProvider provider;

    /**
     * Loads the tool texts and binds them to {@link DoraTools}.
     *
     * @param tools the tool methods
     */
    @Inject
    public DoraToolCatalog(DoraTools tools) {
        this.provider = new DoraToolProvider(tools, DoraToolTexts.load());
    }

    @Override
    public ToolProvider get() {
        return provider;
    }
}
