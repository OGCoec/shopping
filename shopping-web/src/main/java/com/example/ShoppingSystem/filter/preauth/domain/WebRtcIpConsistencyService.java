package com.example.ShoppingSystem.filter.preauth.domain;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import jakarta.servlet.http.HttpServletRequest;
public interface WebRtcIpConsistencyService {
    public static final String STATUS_OK = "ok";

    public static final String ERROR_CODE_MISMATCH = "WEBRTC_IP_MISMATCH";

    public static final String ERROR_MESSAGE_MISMATCH = "网络环境异常，请关闭 VPN/代理后重试";

    public static final String ERROR_CODE_SIGNAL_REQUIRED = "WEBRTC_SIGNAL_REQUIRED";

    public static final String ERROR_MESSAGE_SIGNAL_REQUIRED = "网络环境校验失败，请关闭 VPN/代理后重试";

    public record CheckResult(boolean allowed,
                                  String errorCode,
                                  String message,
                                  String httpIp,
                                  String webRtcIps,
                                  String webRtcStatus) {

            public static CheckResult allow() {
                return new CheckResult(true, "", "", "", "", "");
            }

            public static CheckResult block(String httpIp, String webRtcIps, String webRtcStatus) {
                return new CheckResult(
                        false,
                        ERROR_CODE_MISMATCH,
                        ERROR_MESSAGE_MISMATCH,
                        httpIp,
                        webRtcIps,
                        webRtcStatus
                );
            }

            public static CheckResult blockRequired() {
                return blockRequired("", "", "");
            }

            public static CheckResult blockRequired(String httpIp, String webRtcIps, String webRtcStatus) {
                return new CheckResult(
                        false,
                        ERROR_CODE_SIGNAL_REQUIRED,
                        ERROR_MESSAGE_SIGNAL_REQUIRED,
                        httpIp,
                        webRtcIps,
                        webRtcStatus
                );
            }
        }

    public CheckResult checkAndPersist(HttpServletRequest request);

    public PreAuthBinding applyRequestState(PreAuthBinding binding, HttpServletRequest request);
}
