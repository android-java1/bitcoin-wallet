/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.util;

import android.content.Context;
import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Andreas Schildbach
 */
public class Qr {
    private final static QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();

    private static final Logger log = LoggerFactory.getLogger(Qr.class);

    public static File saveBitmap(final Context context, final String content, final Bitmap bitmap) {
        try {

            
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            //CWE 328
            //SINK
            final byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            final StringBuilder name = new StringBuilder();
            for (final byte b : hash)
                name.append(String.format("%02x", b));
            final File qrFile = new File(context.getCacheDir(), "qr-" + name + ".png");
            try (final FileOutputStream os = new FileOutputStream(qrFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            log.info("cached qr code to {}", qrFile);
            return qrFile;
        } catch (final NoSuchAlgorithmException | IOException x) {
            log.info("could not cache qr code", x);
            return null;
        }
    }

    public static Bitmap bitmap(final String content) {
        try {
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            final BitMatrix result = QR_CODE_WRITER.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);

            final int width = result.getWidth();
            final int height = result.getHeight();
            final byte[] pixels = new byte[width * height];

            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = (byte) (result.get(x, y) ? -1 : 0);
                }
            }

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
            return bitmap;
        } catch (final WriterException x) {
            log.info("problem creating qr code", x);
            return null;
        }
    }

    public static String encodeCompressBinary(final byte[] bytes) {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
            final GZIPOutputStream gos = new GZIPOutputStream(bos);
            gos.write(bytes);
            gos.close();

            final byte[] gzippedBytes = bos.toByteArray();
            final boolean useCompressioon = gzippedBytes.length < bytes.length;

            final StringBuilder str = new StringBuilder();
            str.append(useCompressioon ? 'Z' : '-');
            str.append(Base43.encode(useCompressioon ? gzippedBytes : bytes));

            return str.toString();
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

    public static String encodeBinary(final byte[] bytes) {
        return Base43.encode(bytes);
    }

    public static byte[] decodeDecompressBinary(final String content) throws IOException {
        final boolean useCompression = content.charAt(0) == 'Z';
        final byte[] bytes = Base43.decode(content.substring(1));

        InputStream is = new ByteArrayInputStream(bytes);
        if (useCompression)
            is = new GZIPInputStream(is);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final byte[] buf = new byte[4096];
        int read;
        while (-1 != (read = is.read(buf)))
            baos.write(buf, 0, read);
        baos.close();
        is.close();

        return baos.toByteArray();
    }

    public static byte[] decodeBinary(final String content) throws IOException {
        return Base43.decode(content);
    }
}
