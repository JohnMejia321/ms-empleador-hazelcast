package com.inner.consulting.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.inner.consulting.entities.Empleador;
import com.inner.consulting.services.EmpleadorService;

@Controller
public class EmpleadorController {

    @Autowired
    private EmpleadorService empleadorService;

    @PostMapping("/agregar-empleador")
    public String saveEmpleador(@RequestParam("nombre") String nombre,
            @RequestParam("apellido") String apellido,
            @RequestParam("pdfFile") MultipartFile pdfFile,
            Model model) {
        try {
            Empleador empleador = empleadorService.saveEmpleador(nombre, apellido, pdfFile);
            model.addAttribute("empleador", empleador);
            return "success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }
}
