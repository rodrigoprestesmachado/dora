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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalizes process ("processo") numbers in the unified CNJ
 * format defined by CNJ Resolution nº 65/2008.
 *
 * <p>The canonical format is {@code NNNNNNN-DD.AAAA.J.TR.OOOO} (20 digits in
 * total), e.g. {@code 5033013-66.2026.8.21.0022}. Accepts the number with or
 * without the standard punctuation, as long as exactly 20 digits are present
 * in the right order.
 *
 * @author Rodrigo Prestes Machado
 */
public final class CnjProcessNumberValidator {

    /** Total number of digits in a CNJ process number. */
    private static final int DIGIT_COUNT = 20;

    /** Matches the canonical, fully punctuated CNJ format. */
    private static final Pattern CANONICAL_FORMAT = Pattern
            .compile("^(\\d{7})-(\\d{2})\\.(\\d{4})\\.(\\d)\\.(\\d{2})\\.(\\d{4})$");

    private CnjProcessNumberValidator() {
    }

    /**
     * Checks whether the given value is a valid CNJ process number, with or
     * without punctuation.
     *
     * @param rawProcessNumber the value to validate
     * @return {@code true} if it normalizes to a valid CNJ process number
     */
    public static boolean isValid(String rawProcessNumber) {
        return normalize(rawProcessNumber) != null;
    }

    /**
     * Normalizes a process number to the canonical, fully punctuated CNJ format
     * ({@code NNNNNNN-DD.AAAA.J.TR.OOOO}).
     *
     * @param rawProcessNumber the process number to normalize, with or without punctuation
     * @return the canonical CNJ process number, or {@code null} if the input does not
     *         contain exactly 20 digits in a valid arrangement
     */
    public static String normalize(String rawProcessNumber) {
        if (rawProcessNumber == null) {
            return null;
        }

        String trimmed = rawProcessNumber.trim();
        Matcher canonical = CANONICAL_FORMAT.matcher(trimmed);
        if (canonical.matches()) {
            return format(canonical.group(1), canonical.group(2), canonical.group(3),
                    canonical.group(4), canonical.group(5), canonical.group(6));
        }

        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() != DIGIT_COUNT) {
            return null;
        }

        return format(
                digits.substring(0, 7),
                digits.substring(7, 9),
                digits.substring(9, 13),
                digits.substring(13, 14),
                digits.substring(14, 16),
                digits.substring(16, 20));
    }

    private static String format(String sequential, String verifyingDigit, String year,
            String segment, String court, String origin) {
        return sequential + "-" + verifyingDigit + "." + year + "." + segment + "." + court + "." + origin;
    }
}
