package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;

/** Company and partner logos: PNG or JPEG up to 200 KB, re-encoded before they are stored. */
public final class LogoManager {

    private LogoManager() {}

    /** A logo upload (company or partner): PNG or JPEG, up to 200 KB, re-encoded as PNG. */
    public static byte[] normalize(byte[] image) {
        boolean png = image != null && image.length > 8 && (image[0] & 0xff) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
        boolean jpeg = image != null && image.length > 3 && (image[0] & 0xff) == 0xFF && (image[1] & 0xff) == 0xD8;
        if (!png && !jpeg) {
            throw ApiException.invalid("invalid_request", "El logo debe ser una imagen PNG o JPEG.", null);
        }
        if (image.length > 200 * 1024) {
            throw ApiException.invalid("invalid_request", "El logo no debe pasar de 200 KB.", null);
        }
        return reencode(image);
    }

    /**
     * Checks the declared size before decoding (a small file can declare a huge image) and re-encodes
     * it as PNG, so every PDF render decodes a known-small image.
     */
    private static byte[] reencode(byte[] image) {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(image))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw ApiException.invalid("invalid_request", "No se pudo leer el logo.", null);
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > 2000 || reader.getHeight(0) > 2000) {
                    throw ApiException.invalid("invalid_request", "El logo no debe pasar de 2000 × 2000 píxeles.", null);
                }
                java.awt.image.BufferedImage decoded = reader.read(0);
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(decoded, "png", out);
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (java.io.IOException e) {
            throw ApiException.invalid("invalid_request", "No se pudo leer el logo.", null);
        }
    }
}
