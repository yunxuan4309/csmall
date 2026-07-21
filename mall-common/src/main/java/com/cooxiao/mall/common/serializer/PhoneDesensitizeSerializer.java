package com.cooxiao.mall.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 手机号脱敏序列化器：13812345678 → 138****5678。
 * 在 VO/DTO 的 phone 字段上加 @JsonSerialize(using = PhoneDesensitizeSerializer.class) 即可。
 */
public class PhoneDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null || value.length() < 7) {
            gen.writeString(value);
            return;
        }
        gen.writeString(value.substring(0, 3) + "****" + value.substring(value.length() - 4));
    }
}
