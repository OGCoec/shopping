package com.example.ShoppingSystem.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategory {

    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer level;
    private String path;
    private Integer sortOrder;
    private String iconUrls;
    private String description;
    private String status;
    private Boolean leaf;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
