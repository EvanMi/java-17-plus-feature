package com.yumi.grpc.service;

import com.yumi.grpc.service.grpc.GreetingsGrpc;
import com.yumi.grpc.service.grpc.GreetingsRequest;
import com.yumi.grpc.service.grpc.GreetingsResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class ServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

}

@Service
class DefaultGreetingsService extends GreetingsGrpc.GreetingsImplBase {

    @Override
    public void hello(GreetingsRequest request, StreamObserver<GreetingsResponse> responseObserver) {
       responseObserver.onNext(
               GreetingsResponse.newBuilder()
                       .setResult("hello, " + request.getName() + "!")
                       .build()
       );
       responseObserver.onCompleted();
    }
}