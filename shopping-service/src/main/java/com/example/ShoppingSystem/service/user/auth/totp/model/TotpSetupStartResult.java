package com.example.ShoppingSystem.service.user.auth.totp.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TotpSetupStartResult {

    boolean success;
    String message;
    String secret;
    String otpauthUri;
    int secretBits;
    int digits;
    int periodSeconds;
}
