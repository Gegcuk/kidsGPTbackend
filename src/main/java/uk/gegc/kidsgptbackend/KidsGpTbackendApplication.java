package uk.gegc.kidsgptbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class KidsGpTbackendApplication {

    public static void main(String[] args) {

        SpringApplication.run(KidsGpTbackendApplication.class, args);
    }

}
