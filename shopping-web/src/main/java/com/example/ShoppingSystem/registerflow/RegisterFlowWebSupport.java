package com.example.ShoppingSystem.registerflow;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;

/**
 * Shared register flow route and notice helpers.
 */
public final class RegisterFlowWebSupport {

    public static final String CREATE_ACCOUNT_PATH = "/shopping/user/create-account";
    public static final String CREATE_ACCOUNT_PASSWORD_PATH = "/shopping/user/create-account/password";
    public static final String EMAIL_VERIFICATION_PATH = "/shopping/user/email-verification";
    public static final String ADD_PHONE_PATH = "/shopping/user/add-phone";
    public static final String REGISTER_MODE = "register";
    public static final String MODE_QUERY_PARAM = "mode";
    public static final String LOGIN_PATH = "/shopping/user/log-in";
    public static final String SESSION_ENDED_PATH = "/shopping/user/session-ended";

    public static final String NOTICE_QUERY_PARAM = "register_notice";
    public static final String NOTICE_FLOW_EXPIRED = "flow-expired";
    public static final String NOTICE_STEP_RESTORED = "step-restored";
    public static final String NOTICE_REGISTER_COMPLETED = "register-completed";

    public static final Set<String> GUARDED_PAGE_PATHS = Set.of(
            CREATE_ACCOUNT_PASSWORD_PATH,
            EMAIL_VERIFICATION_PATH,
            ADD_PHONE_PATH
    );

    private RegisterFlowWebSupport() {
    }

    public static boolean isGuardedPagePath(String path) {
        return GUARDED_PAGE_PATHS.contains(path);
    }

    public static String pathForStep(RegisterFlowStep step) {
        if (step == null) {
            return CREATE_ACCOUNT_PATH;
        }
        return switch (step) {
            case PASSWORD -> CREATE_ACCOUNT_PASSWORD_PATH;
            case EMAIL_VERIFICATION -> EMAIL_VERIFICATION_PATH;
            case ADD_PHONE -> ADD_PHONE_PATH;
            case DONE -> LOGIN_PATH;
        };
    }

    public static String routeForStep(RegisterFlowStep step) {
        String path = pathForStep(step);
        if (EMAIL_VERIFICATION_PATH.equals(path) || ADD_PHONE_PATH.equals(path)) {
            return withMode(path, REGISTER_MODE);
        }
        return path;
    }

    public static String withNotice(String path, String notice) {
        if (path == null || path.isBlank()) {
            path = CREATE_ACCOUNT_PATH;
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
