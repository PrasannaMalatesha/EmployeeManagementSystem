package com.malatesha.ems;

import org.springframework.boot.SpringApplication;

public class TestEmsApplication {

	public static void main(String[] args) {
		SpringApplication.from(EmsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
