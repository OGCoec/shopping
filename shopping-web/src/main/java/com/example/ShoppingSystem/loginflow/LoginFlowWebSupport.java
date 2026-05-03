package com.example.ShoppingSystem.loginflow;

import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;

public final class LoginFlowWebSupport {

    public static final String LOGIN_PATH = "/shopping/user/log-in";
    public static final String LOGIN_PASSWORD_PATH = "/shopping/user/log-in/password";
    public static final String EMAIL_VERIFICATION_PATH = "/shopping/user/email-verification";
    public static final String TOTP_VERIFICATION_PATH = "/shopping/user/totp-verification";
    public static final String ADD_PHONE_PATH = "/shopping/user/add-phone";
    public static final String LOGIN_MODE = "login";
    public static final String MODE_QUERY_PARAM = "mode";
    public static final String SESSION_ENDED_PATH = "/shopping/user/session-ended";
    public static final String AUTHENTICATED_PATH = "/";

    public static final String NOTICE_QUERY_PARAM = "login_notice";
    public static final String NOTICE_FLOW_EXPIRED = "flow-expired";
    public static final String NOTICE_STEP_RESTORED = "step-restored";
    public static final String NOTICE_LOGIN_COMPLETED = "login-completed";

    public static final Set<String> GUARDED_PAGE_PATHS = Set.of(
            LOGIN_PASSWORD_PATH,
            EMAIL_VERIFICATION_PATH,
            TOTP_VERIFICATION_PATH,
            ADD_PHONE_PATH
    );

    private LoginFlowWebSupport() {
    }

    public static boolean isGuardedPagePath(String path) {
        return GUARDED_PAGE_PATHS.contains(path);
    }

    public static boolean isSharedWithRegisterPath(String path) {
        return EMAIL_VERIFICATION_PATH.equals(path) || ADD_PHONE_PATH.equals(path);
    }

    public static String pathForStep(LoginFlowStep step) {
        if (step == null) {
            return LOGIN_PATH;
        }
        return switch (step) {
            case PASSWORD -> LOGIN_PASSWORD_PATH;
            case EMAIL_VERIFICATION -> EMAIL_VERIFICATION_PATH;
            case TOTP_VERIFICATION -> TOTP_VERIFICATION_PATH;
            case ADD_PHONE -> ADD_PHONE_PATH;
            case DONE -> AUTHENTICATED_PATH;
            case BLOCKED, OPERATION_TIMEOUT -> LOGIN_PATH;
        };
    }

    public static String routeForStep(LoginFlowStep step) {
        String path = pathForStep(step);
        if (EMAIL_VERIFICATION_PATH.equals(path)
                || TOTP_VERIFICATION_PATH.equals(path)
                || ADD_PHONE_PATH.equals(path)) {
            return withMode(path, LOGIN_MODE);
        }
        return path;
    }

    public static LoginFactor factorForPath(String path) {
        if (LOGIN_PASSWORD_PATH.equals(path)) {
            return LoginFactor.PASSWORD;
        }
        if (EMAIL_VERIFICATION_PATH.equals(path)) {
            return LoginFactor.EMAIL_OTP;
        }
        if (TOTP_VERIFICATION_PATH.equals(path)) {
            return LoginFactor.TOTP;
        }
        return null;
    }

    public static boolean isAllowedFactorPath(LoginFlowSession session, String path) {
        LoginFactor factor = factorForPath(path);
        if (session == null || factor == null) {
            return false;
        }
        return session.getAvailableFactors().contains(factor)
                && !session.getCompletedFactors().contains(factor)
                && !session.isCompleted()
                && session.getStep() != LoginFlowStep.ADD_PHONE
                && session.getStep() != LoginFlowStep.DONE;
    }

    public static String withNotice(String path, String notice) {
        if (path == null || path.isBlank()) {
            path = LOGIN_PATH;
        }
        if (notice == null || notice.isBlank()) {
            return path;
        }
        return UriComponentsBuilder.fromUriString(path)
                .queryParam(NOTICE_QUERY_PARAM, notice)
                .build()
                .toUriString();
    }

    public static String withMode(String path, String mode) {
        if (path == null || path.isBlank() || mode == null || mode.isBlank()) {
            return path;
        }
        return UriComponentsBuilder.fromPath(path)
                .queryParam(MODE_QUERY_PARAM, mode)
                .build()
                .toUriString();
    }

    public static String sessionEndedWithNotice() {
        return withNotice(SESSION_ENDED_PATH, NOTICE_FLOW_EXPIRED);
    }
}
