package com.hinglishpdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HinglishPdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(HinglishPdfApplication.class, args);
    }
}
