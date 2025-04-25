package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for testing OAuth2 flow with a simple Thymeleaf client.
 * This controller is purely for UI testing purposes and does not handle authentication logic directly.
 * All authentication is processed through the AuthController via REST API calls.
 */
@Controller
@RequestMapping("/test-client")
public class TestClientController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    /**
     * Displays the login page with Google OAuth button.
     * 
     * @param model The Spring MVC model for adding attributes
     * @return The Thymeleaf template name
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("googleClientId", googleClientId);
        return "test-login";
    }

    /**
     * Handles the OAuth2 callback and displays token information.
     * This endpoint only displays the authorization code but doesn't process it directly.
     * The frontend JavaScript will call the actual /auth/login API endpoint.
     * 
     * @param code The authorization code returned from Google OAuth2
     * @param model The Spring MVC model for adding attributes
     * @return The Thymeleaf template name
     */
    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code, Model model) {
        model.addAttribute("authorizationCode", code);
        return "test-callback";
    }

    /**
     * Dashboard page to test token functionality and API calls.
     * This page allows testing authenticated API requests using the obtained tokens.
     * 
     * @return The Thymeleaf template name
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "test-dashboard";
    }
}
