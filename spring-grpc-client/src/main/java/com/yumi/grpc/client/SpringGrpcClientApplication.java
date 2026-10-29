package com.yumi.grpc.client;

import com.yumi.grpc.client.grpc.GreetingsGrpc;
import com.yumi.grpc.client.grpc.GreetingsRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootApplication
@ImportGrpcClients(basePackageClasses = GreetingsGrpc.class,
target = "127.0.0.1:9090")
public class SpringGrpcClientApplication {

	static void main(String[] args) {
		SpringApplication.run(SpringGrpcClientApplication.class, args);
	}

}


@Controller
@ResponseBody
class ClientController {
	private final GreetingsGrpc.GreetingsBlockingStub greetings;

    ClientController(GreetingsGrpc.GreetingsBlockingStub greetings) {
        this.greetings = greetings;
    }

	@GetMapping("/hello")
	String hello() {
		return this.greetings.hello(
				GreetingsRequest.newBuilder()
						.setName("world").build()
		).getResult();
	}
}