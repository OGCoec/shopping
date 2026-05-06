package com.example.ShoppingSystem.controller.user.profile;

import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.service.user.profile.UserAvatarService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/shopping/user/profile/avatar")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    public UserAvatarController(UserAvatarService userAvatarService) {
        this.userAvatarService = userAvatarService;
    }

    @PostMapping
    public ResponseEntity<AvatarActionResponse> uploadAvatar(@RequestParam("file") MultipartFile[] files,
                                                             Authentication authentication,
                                                             HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        if (files == null || files.length != 1) {
            return ResponseEntity.badRequest().body(new AvatarActionResponse(false, "Only one avatar image can be uploaded at a time.", null));
        }
        MultipartFile file = files[0];
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new AvatarActionResponse(false, "Avatar image is required.", null));
        }

        try {
            userAvatarService.submitAvatarUpload(
                    userId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AvatarActionResponse(true, "Avatar upload job has been submitted.", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new AvatarActionResponse(false, ex.getMessage(), null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AvatarActionResponse(false, "Failed to submit avatar upload job.", null));
        }
    }

    @DeleteMapping
    public ResponseEntity<AvatarActionResponse> deleteAvatar(Authentication authentication,
                                                             HttpServletRequest request) {
        Long userId = requireCurrentUserId(authentication, request);
        try {
            boolean deleted = userAvatarService.deleteAvatar(userId);
            if (!deleted) {
                return ResponseEntity.ok(new AvatarActionResponse(true, "There is no avatar to delete.", null));
            }
            return ResponseEntity.ok(new AvatarActionResponse(true, "Avatar deleted.", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new AvatarActionResponse(false, ex.getMessage(), null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AvatarActionResponse(false, "Failed to delete avatar.", null));
        }
    }

    private Long requireCurrentUserId(Authentication authentication, HttpServletRequest request) {
        Object requestUserId = request == null ? null : request.getAttribute("authUserId");
        if (requestUserId instanceof Number number) {
            return number.longValue();
        }
        if (requestUserId instanceof String text) {
            return parseUserId(text);
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext context) {
            return context.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not authenticated.");
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record AvatarActionResponse(boolean success, String message, String avatarUrl) {
    }
}
