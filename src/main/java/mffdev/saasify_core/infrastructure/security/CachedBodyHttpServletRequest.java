package mffdev.saasify_core.infrastructure.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Buffers the request body so the signature can be verified before the payload is read,
 * without consuming the stream that the controller needs afterwards.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        this.body = request.getInputStream().readNBytes(maxBodyBytes + 1);
    }

    boolean exceeds(int maxBodyBytes) {
        return body.length > maxBodyBytes;
    }

    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream content = new ByteArrayInputStream(body);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return content.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Leitura assíncrona não suportada");
            }

            @Override
            public int read() {
                return content.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
    }

    private java.nio.charset.Charset charset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }

        try {
            return java.nio.charset.Charset.forName(encoding);
        } catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }
}