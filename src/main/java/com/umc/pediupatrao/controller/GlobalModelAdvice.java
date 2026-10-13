package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;


@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    @ModelAttribute("currentRole")
    public String currentRole() {
        return CurrentUser.roleOrSystem();
    }
}
