/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.in.rest;

import dev.dora.domain.port.in.ChatUseCase;
import dev.dora.domain.validation.BrazilianPhoneValidator;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource that exposes the Dora chat endpoint.
 *
 * @author Rodrigo Prestes Machado
 */
@Path("/dora")
public class DoraResource {

    /**
     * Driving port used to process chat requests.
     */
    private final ChatUseCase chatUseCase;

    /**
     * Creates the REST resource with the chat use case.
     *
     * @param chatUseCase application port for chatting
     */
    @Inject
    public DoraResource(ChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    /**
     * Streams a chat reply for the submitted user message.
     *
     * @param phoneNumber phone number that identifies the user
     * @param message the user message submitted as form data
     * @return a multi that emits the response as plain text chunks
     * @throws WebApplicationException with a 400 status if {@code phoneNumber} is not
     *         a valid Brazilian phone number
     */
    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> chat(
            @FormParam("phoneNumber") String phoneNumber,
            @FormParam("message") String message) {
        String normalizedPhoneNumber = BrazilianPhoneValidator.normalize(phoneNumber);
        if (normalizedPhoneNumber == null) {
            throw new WebApplicationException(
                    "Informe um telefone válido para o Brasil, com DDD.",
                    Response.Status.BAD_REQUEST);
        }
        return WebChatProgress.interleave(normalizedPhoneNumber,
                chatUseCase.chat(normalizedPhoneNumber, message));
    }

}
