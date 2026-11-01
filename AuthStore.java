package com.example.premiumauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AuthStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ITER = 120_000;

    private static final class Account { String salt; String hash; }

    private final Path file;
    private Map<String, Account> data = new HashMap<>();

    public AuthStore(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try {
                Map<String, Account> m = GSON.fromJson(Files.readString(file),
                        new TypeToken<Map<String, Account>>() {}.getType());
                if (m != null) data = m;
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private static String key(String n) { return n.toLowerCase(Locale.ROOT); }

    public synchronized boolean has(String name) { return data.containsKey(key(name)); }

    public synchronized void register(String name, String pw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Account a = new Account();
        a.salt = Base64.getEncoder().encodeToString(salt);
        a.hash = hash(pw, salt);
        data.put(key(name), a);
        save();
    }

    public synchronized boolean check(String name, String pw) {
        Account a = data.get(key(name));
        if (a == null) return false;
        byte[] expected = Base64.getDecoder().decode(a.hash);
        byte[] actual = Base64.getDecoder().decode(hash(pw, Base64.getDecoder().decode(a.salt)));
        return MessageDigest.isEqual(expected, actual);
    }

    private static String hash(String pw, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(
                    f.generateSecret(new PBEKeySpec(pw.toCharArray(), salt, ITER, 256)).getEncoded());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void save() {
        try { Files.writeString(file, GSON.toJson(data)); }
        catch (IOException e) { e.printStackTrace(); }
    }
}
