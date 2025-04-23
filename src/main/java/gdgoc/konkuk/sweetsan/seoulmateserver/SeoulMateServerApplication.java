package gdgoc.konkuk.sweetsan.seoulmateserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class SeoulMateServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeoulMateServerApplication.class, args);
    }
}
