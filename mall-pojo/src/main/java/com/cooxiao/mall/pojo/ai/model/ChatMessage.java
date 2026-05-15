package com.cooxiao.mall.pojo.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    private String role;
    private String content;
    private LocalDateTime timestamp;
}
