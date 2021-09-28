package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeController.class);
    private final Tracer tracer;
    private final FooProducer producer;

    @Autowired
    public HomeController(Tracer tracer, FooProducer producer) {
        this.tracer = tracer;
        this.producer = producer;
    }

    @RequestMapping("/")
    public String get() {
        LOGGER.info("Handling home");
        return "Hello World";
    }

    @RequestMapping("/send")
    public String send(@RequestParam int number) {
        LOGGER.info("Send {}", number);
        return producer.send(number).thenReturn("Events sent:" +  number).block();
    }
}
