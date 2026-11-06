package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@GetMapping("/hello")
	public String hello() {
		return "Hello World! My first Spring Boot API";
	}

	@GetMapping("/greet")
	public String greet(@RequestParam String name) {
		return "Hello " + name;
	}
	@GetMapping("/student")
	public String student(
			@RequestParam String name,
			@RequestParam int age){
		return "student Name:"+ name +" , Age: "+age;
	}

}