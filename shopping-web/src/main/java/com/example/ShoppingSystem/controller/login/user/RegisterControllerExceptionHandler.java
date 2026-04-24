package com.example.ShoppingSystem.controller.login.user;

import com.example.ShoppingSystem.common.exception.ChallengeTypeMismatchException;
import com.example.ShoppingSystem.controller.login.user.dto.RegisterSendEmailCodeResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegisterController.class)
public class RegisterControllerExceptionHandler {

    @ExceptionHandler(ChallengeTypeMismatchException.class)
    public RegisterSendEmailCodeResponse handleChallengeTypeMismatch(ChallengeTypeMismatchException e) {
        return RegisterSendEmailCodeResponse.builder()
                .success(false)
                .message(e.getMessage())
                .challengeType(e.getExpectedChallengeType())
                .challengeSubType(e.getExpectedChallengeSubType())
                .challengeSiteKey(e.getChallengeSiteKey())
                .emailCodeSent(false)
                .build();
    }
}
