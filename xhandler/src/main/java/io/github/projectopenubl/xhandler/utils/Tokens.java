package io.github.projectopenubl.xhandler.utils;

import io.github.projectopenubl.xhandler.files.FileStore;
import java.nio.charset.StandardCharsets;

/** API tokens are stored as hashes only. */
public final class Tokens {

    private Tokens() {}

    public static String hash(String token) {
        return FileStore.sha256(token.getBytes(StandardCharsets.UTF_8));
    }
}
