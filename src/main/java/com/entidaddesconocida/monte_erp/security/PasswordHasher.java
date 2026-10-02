package com.entidaddesconocida.monte_erp.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing and verification with PBKDF2-HMAC-SHA256 (JDK only, no
 * external dependencies).
 *
 * <p>Stored format, self describing so the parameters can be changed in the
 * future without invalidating the existing hashes:</p>
 *
 * <pre>pbkdf2-sha256$&lt;iterations&gt;$&lt;salt in Base64&gt;$&lt;key in Base64&gt;</pre>
 *
 * <p>The comparison uses {@link MessageDigest#isEqual} (constant time) so no
 * information leaks through timing.</p>
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_ID = "pbkdf2-sha256";
    private static final String SEPARATOR = "$";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Utility class: never instantiated.
    }

    /**
     * Hashes a password with a brand new random salt.
     *
     * @throws IllegalArgumentException when the password is null or empty
     */
    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        Base64.Encoder encoder = Base64.getEncoder();
        return FORMAT_ID + SEPARATOR + ITERATIONS + SEPARATOR
                + encoder.encodeToString(salt) + SEPARATOR + encoder.encodeToString(key);
    }

    /**
     * Checks a password against a stored hash.
     *
     * @return {@code true} only when the password is correct; {@code false} for
     *         unknown or corrupted formats (never throws)
     */
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String[] parts = storedHash.split("\\" + SEPARATOR);
        if (parts.length != 4 || !FORMAT_ID.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations <= 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyLengthBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not compute the PBKDF2 hash", e);
        } finally {
            spec.clearPassword();
        }
    }
}
