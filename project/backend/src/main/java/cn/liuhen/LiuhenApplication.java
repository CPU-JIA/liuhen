package cn.liuhen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LiuhenApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiuhenApplication.class, args);
    }
}
