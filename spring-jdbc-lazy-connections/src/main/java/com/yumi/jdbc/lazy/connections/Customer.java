package com.yumi.jdbc.lazy.connections;

import org.springframework.data.annotation.Id;

public record Customer(@Id Long id, String name) {
}
