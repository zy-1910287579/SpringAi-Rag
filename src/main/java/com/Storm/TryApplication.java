package com.Storm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan("com.Storm.mapper")
@SpringBootApplication
public class TryApplication {

	public static void main(String[] args) {
		SpringApplication.run(TryApplication.class, args);
	}

}
