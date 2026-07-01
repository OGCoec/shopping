package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuPageResponse;
public interface AdminProductSpuSearchService {
    public AdminProductSpuPageResponse searchPage(String name,
                                                  Long categoryId,
                                                  String status,
                                                  int page,
                                                  int pageSize);
}
