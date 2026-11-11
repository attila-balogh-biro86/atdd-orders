package com.example.orders.web;

import com.example.orders.domain.CustomerNotFoundException;
import com.example.orders.domain.InvalidOrderException;
import com.example.orders.domain.OrderAlreadyCancelledException;
import com.example.orders.domain.OrderNotFoundException;
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

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail orderNotFound(OrderNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order not found");
        return pd;
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ProblemDetail orderAlreadyCancelled(OrderAlreadyCancelledException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Order already cancelled");
        return pd;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail customerNotFound(CustomerNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Customer not found");
        return pd;
    }
}
