package com.example.ShoppingSystem.signin.service;

import com.example.ShoppingSystem.mapper.signin.UserSignInMapper;
import com.example.ShoppingSystem.signin.config.UserSignInProperties;
import com.example.ShoppingSystem.signin.dto.UserSignInResponse;
import com.example.ShoppingSystem.signin.dto.UserSignInStatusResponse;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public interface UserSignInService {
    public UserSignInStatusResponse status(Long userId);

    public UserSignInResponse signIn(Long userId);
}
