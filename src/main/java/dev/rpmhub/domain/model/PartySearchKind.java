/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.domain.model;

/**
 * Outcome of a TJRS search by exact person name.
 *
 * @author Rodrigo Prestes Machado
 */
public enum PartySearchKind {

    /** The court returned no matching person or no active processes. */
    NOT_FOUND,

    /** Several records share the exact name and the client must say which one. */
    AMBIGUOUS_PEOPLE,

    /** Processes of a single person, ready for the client to pick a number. */
    PROCESS_LIST
}
