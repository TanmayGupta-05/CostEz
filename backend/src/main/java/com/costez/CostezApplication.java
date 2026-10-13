package com.costez;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CostezApplication {
  public static void main(String[] args) {
    SpringApplication.run(CostezApplication.class, args);
  }
}
