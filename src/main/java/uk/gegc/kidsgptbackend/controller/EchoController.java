package uk.gegc.kidsgptbackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {

    @GetMapping("/echo")
    public String echo(@RequestParam(value = "msg", defaultValue = "Hello") String message){
        return message + "1";
    }

}
