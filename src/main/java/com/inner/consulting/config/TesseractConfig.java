package com.inner.consulting.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Bean
    public Tesseract getTesseract() {
        Tesseract instance = new Tesseract();
        String rootPath = System.getProperty("user.dir");
        String tessdataPath = TesseractConfig.class.getResource("/tessdata").getPath();
        instance.setDatapath(rootPath + "/tessdata");
        instance.setLanguage("spa");
        return instance;
    }
}
