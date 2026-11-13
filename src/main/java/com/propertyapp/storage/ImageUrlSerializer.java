package com.propertyapp.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Writes a stored image reference as the address browsers load (ADR-0016). Put on every DTO field
 * that carries an image: {@code @JsonSerialize(using = ImageUrlSerializer.class)}.
 */
public class ImageUrlSerializer extends StdSerializer<String> {

    public ImageUrlSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(ImageRefs.resolveCurrent(value));
    }
}
