package com.example.orders.web;

import com.example.orders.domain.CustomerNotFoundException;
import com.example.orders.domain.InvalidOrderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail invalidOrder(InvalidOrderException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid order");
        return pd;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail customerNotFound(CustomerNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Customer not found");
        return pd;
    }
}
