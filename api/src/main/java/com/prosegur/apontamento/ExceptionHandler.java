package com.prosegur.apontamento;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.zalando.problem.spring.web.advice.general.GeneralAdviceTrait;
import org.zalando.problem.spring.web.advice.http.HttpAdviceTrait;
import org.zalando.problem.spring.web.advice.io.IOAdviceTrait;
import org.zalando.problem.spring.web.advice.routing.RoutingAdviceTrait;
import org.zalando.problem.spring.web.advice.validation.ValidationAdviceTrait;

@ControllerAdvice
public class ExceptionHandler implements GeneralAdviceTrait, HttpAdviceTrait, IOAdviceTrait, RoutingAdviceTrait, ValidationAdviceTrait {}