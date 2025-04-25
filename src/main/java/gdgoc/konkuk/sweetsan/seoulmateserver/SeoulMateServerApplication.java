package gdgoc.konkuk.sweetsan.seoulmateserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@EnableAsync
public class SeoulMateServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeoulMateServerApplication.class, args);
    }
}
