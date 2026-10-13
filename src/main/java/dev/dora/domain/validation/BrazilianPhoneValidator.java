/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.validation;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalizes Brazilian phone numbers.
 *
 * <p>Accepts mobile numbers (9 digits, starting with {@code 9}) and landline
 * numbers (8 digits, starting with {@code 2}-{@code 5}), preceded by a valid
 * two-digit area code ({@code DDD}), with or without the {@code 55} country
 * code and regardless of extra formatting characters such as spaces,
 * parentheses, dashes or a leading {@code +}.
 *
 * @author Rodrigo Prestes Machado
 */
public final class BrazilianPhoneValidator {

    /** ISO country calling code for Brazil. */
    private static final String COUNTRY_CODE = "55";

    /** Valid Brazilian area codes (DDDs), as defined by Anatel. */
    private static final Set<String> VALID_AREA_CODES = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99");

    /** Mobile subscriber number: 9 digits, always starting with 9. */
    private static final Pattern MOBILE_SUBSCRIBER = Pattern.compile("^9[1-9]\\d{7}$");

    /** Landline subscriber number: 8 digits, starting with 2 to 5. */
    private static final Pattern LANDLINE_SUBSCRIBER = Pattern.compile("^[2-5]\\d{7}$");

    private BrazilianPhoneValidator() {
    }

    /**
     * Checks whether the given phone number is a valid Brazilian phone number.
     *
     * @param rawPhoneNumber the phone number to validate, in any common format
     * @return {@code true} if the number is a valid Brazilian mobile or landline number
     */
    public static boolean isValid(String rawPhoneNumber) {
        return normalize(rawPhoneNumber) != null;
    }

    /**
     * Normalizes a Brazilian phone number to its canonical digits-only form,
     * prefixed with the {@code 55} country code (e.g. {@code 5511987654321}).
     *
     * @param rawPhoneNumber the phone number to normalize, in any common format
     * @return the normalized phone number, or {@code null} if it is not a valid
     *         Brazilian phone number
     */
    public static String normalize(String rawPhoneNumber) {
        if (rawPhoneNumber == null) {
            return null;
        }

        String digits = rawPhoneNumber.replaceAll("\\D", "");
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith(COUNTRY_CODE)) {
            digits = digits.substring(COUNTRY_CODE.length());
        }

        if (digits.length() != 10 && digits.length() != 11) {
            return null;
        }

        String areaCode = digits.substring(0, 2);
        String subscriber = digits.substring(2);

        if (!VALID_AREA_CODES.contains(areaCode)) {
            return null;
        }

        boolean validSubscriber = (subscriber.length() == 9
                && MOBILE_SUBSCRIBER.matcher(subscriber).matches())
                || (subscriber.length() == 8 && LANDLINE_SUBSCRIBER.matcher(subscriber).matches());

        if (!validSubscriber) {
            return null;
        }

        return COUNTRY_CODE + areaCode + subscriber;
    }

}
