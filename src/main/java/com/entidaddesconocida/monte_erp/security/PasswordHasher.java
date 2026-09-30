package com.entidaddesconocida.monte_erp.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Hash y verificación de contraseñas con PBKDF2-HMAC-SHA256 (solo JDK, sin
 * dependencias externas).
 *
 * <p>Formato almacenado, autodescriptivo para poder cambiar los parámetros en
 * el futuro sin invalidar los hashes existentes:</p>
 *
 * <pre>pbkdf2-sha256$&lt;iteraciones&gt;$&lt;salt en Base64&gt;$&lt;clave en Base64&gt;</pre>
 *
 * <p>La comparación usa {@link MessageDigest#isEqual} (tiempo constante) para no
 * filtrar información por temporización.</p>
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
        // Utilidad: no se instancia.
    }

    /**
     * Genera el hash de una contraseña con un salt aleatorio nuevo.
     *
     * @throws IllegalArgumentException si la contraseña es nula o vacía
     */
    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        Base64.Encoder encoder = Base64.getEncoder();
        return FORMAT_ID + SEPARATOR + ITERATIONS + SEPARATOR
                + encoder.encodeToString(salt) + SEPARATOR + encoder.encodeToString(key);
    }

    /**
     * Comprueba una contraseña contra un hash almacenado.
     *
     * @return {@code true} solo si la contraseña es correcta; {@code false} ante
     *         formatos desconocidos o corruptos (nunca lanza excepción)
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
            throw new IllegalStateException("No se pudo calcular el hash PBKDF2", e);
        } finally {
            spec.clearPassword();
        }
    }
}
