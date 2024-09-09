package com.GASB.o365_func;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class O365FuncApplication {

	public static void main(String[] args) {
		SpringApplication.run(O365FuncApplication.class, args);
	}

}
