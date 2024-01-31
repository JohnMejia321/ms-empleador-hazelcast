package com.inner.consulting.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Bean
    public Tesseract getTesseract() {
        Tesseract instance = new Tesseract();
        // Obtiene la ruta absoluta de la carpeta raíz del proyecto
        String rootPath = System.getProperty("user.dir");
        // Obtiene la ruta absoluta del directorio tessdata en el proyecto
        String tessdataPath = TesseractConfig.class.getResource("/tessdata").getPath();
        // Establece la ruta relativa al directorio tessdata
        instance.setDatapath(rootPath + "/tessdata");
        instance.setLanguage("spa");
        return instance;
    }
}
