// Path: src/main/java/com/pdts/PdtsApplication.java
package com.pdts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PdtsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdtsApplication.class, args);
    }
}
