/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.domain.validation;

import java.util.Locale;

/**
 * Validates a person's name for the TJRS "por nome da parte" search.
 *
 * <p>The court's form accepts a name only when at least two words have more
 * than one letter each (particles such as {@code "d"} do not count). The value
 * submitted to the site is the trimmed name in upper case.
 *
 * @author Rodrigo Prestes Machado
 */
public final class PersonNameValidator {

    /** Minimum number of words longer than one character. */
    private static final int MIN_SUBSTANTIAL_WORDS = 2;

    private PersonNameValidator() {
    }

    /**
     * Checks whether the given name can be used in a TJRS party search.
     *
     * @param rawName the name as provided by the client
     * @return {@code true} if it normalizes to a searchable name
     */
    public static boolean isValid(String rawName) {
        return normalize(rawName) != null;
    }

    /**
     * Normalizes a person name to the upper-case form sent to the TJRS search.
     *
     * @param rawName the name as provided by the client
     * @return the upper-case name with collapsed whitespace, or {@code null} when
     *         fewer than two words have more than one letter
     */
    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        String collapsed = rawName.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }

        int substantialWords = 0;
        for (String word : collapsed.split(" ")) {
            if (word.length() > 1) {
                substantialWords++;
            }
        }
        if (substantialWords < MIN_SUBSTANTIAL_WORDS) {
            return null;
        }

        return collapsed.toUpperCase(Locale.ROOT);
    }
}
