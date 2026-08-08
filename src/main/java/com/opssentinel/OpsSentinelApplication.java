package com.opssentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OpsSentinelApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpsSentinelApplication.class, args);
	}

}
