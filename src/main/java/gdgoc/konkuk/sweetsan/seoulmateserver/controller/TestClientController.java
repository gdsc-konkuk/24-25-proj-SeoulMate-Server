package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for testing OAuth2 flow with a simple Thymeleaf client This controller is purely for UI and does not
 * handle authentication logic directly. Authentication is handled by the AuthController via REST API calls.
 */
@Controller
@RequestMapping("/test-client")
public class TestClientController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    /**
     * Displays the login page with Google OAuth button
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("googleClientId", googleClientId);
        return "test-login";
    }

    /**
     * Handles the OAuth2 callback and displays token information This only displays the code but doesn't process it
     * directly. The frontend JavaScript will call the actual /auth/login API.
     */
    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code, Model model) {
        model.addAttribute("authorizationCode", code);
        return "test-callback";
    }

    /**
     * Dashboard page to test token functionality
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "test-dashboard";
    }
}
