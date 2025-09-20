package com.pm.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.rmi.ServerException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class JwtValidationException {

    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    public Mono<Void> handleWebClientResponseException(ServerWebExchange serverWebExchange) {
        serverWebExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return serverWebExchange.getResponse().setComplete();
    }




}
