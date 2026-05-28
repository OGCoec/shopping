package com.example.ShoppingSystem.admin.service.common;

import org.springframework.http.HttpStatus;

public final class AdminPaginationValidator {

    private static final String PAGE_SIZE_INVALID_CODE = "ADMIN_PAGE_SIZE_INVALID";
    private static final String PAGE_SIZE_INVALID_MESSAGE = "pageSize must be a positive integer.";

    private AdminPaginationValidator() {
    }

    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int normalizePage(int page) {
        return Math.max(1, page);
    }

    public static int normalizePageSize(Integer pageSize, int defaultPageSize) {
        if (pageSize == null) {
            return defaultPageSize;
        }
        return normalizePageSize(pageSize.intValue());
    }

    public static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new AdminServiceException(
                    PAGE_SIZE_INVALID_CODE,
                    PAGE_SIZE_INVALID_MESSAGE,
                    HttpStatus.BAD_REQUEST);
        }
        return pageSize;
    }
}
