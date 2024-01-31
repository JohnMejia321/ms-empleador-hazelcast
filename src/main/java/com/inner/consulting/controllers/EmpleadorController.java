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
            // Save the user and the pdf file
            Empleador empleador = empleadorService.saveEmpleador(nombre, apellido, pdfFile);

            // Add the user to the model
            model.addAttribute("empleador", empleador);

            // Return the success view
            return "success";
        } catch (Exception e) {
            // Add the error message to the model
            model.addAttribute("error", e.getMessage());

            // Return the error view
            return "error";
        }
    }
}
