package com.grinderwolf.swm.plugin.loader.redis.util;

import io.lettuce.core.codec.RedisCodec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringByteCodec implements RedisCodec<String, byte[]> {

    public static final StringByteCodec INSTANCE = new StringByteCodec();

    private static final byte[] EMPTY = new byte[0];
    private final Charset charset = StandardCharsets.UTF_8;

    @Override
    public String decodeKey(final ByteBuffer bytes) {
        return charset.decode(bytes).toString();
    }

    @Override
    public byte[] decodeValue(final ByteBuffer bytes) {
        return getBytes(bytes);
    }

    @Override
    public ByteBuffer encodeKey(final String key) {
        return charset.encode(key);
    }

    @Override
    public ByteBuffer encodeValue(final byte[] value) {
        return value != null ? ByteBuffer.wrap(value) : ByteBuffer.wrap(EMPTY);
    }

    private static byte[] getBytes(final ByteBuffer buffer) {
        final byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        return b;
    }

}