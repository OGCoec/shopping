package com.example.ShoppingSystem.entity.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserLoginIdentityBuilderTest {

    @Test
    void shouldBuildIdentityWithBuilder() {
        UserLoginIdentity identity = UserLoginIdentity.builder()
                .id(1L)
                .userId(2L)
                .email("test@example.com")
                .githubId("gh_123")
                .googleId("google_123")
                .status("ACTIVE")
                .build();

        assertEquals(1L, identity.getId());
        assertEquals(2L, identity.getUserId());
        assertEquals("test@example.com", identity.getEmail());
        assertEquals("gh_123", identity.getGithubId());
        assertEquals("google_123", identity.getGoogleId());
        assertEquals("ACTIVE", identity.getStatus());
    }
}
