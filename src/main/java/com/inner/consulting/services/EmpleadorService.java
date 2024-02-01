package com.inner.consulting.services;

import com.inner.consulting.repositories.EmpleadorRepository;
import com.inner.consulting.entities.Empleador;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.sourceforge.tess4j.ITesseract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.Job;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.collection.IList;

import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;


import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

@Service
public class EmpleadorService {

    @Autowired
    private EmpleadorRepository empleadorRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ITesseract tesseract;

    private String minionEndpoint = "http://localhost:9000";
    private String minionBucketName = "my-bucket";

    public Empleador saveEmpleador(String nombre, String apellido, MultipartFile pdfFile) throws Exception {
        try {
            // Generate a unique id for the user
            UUID empleadorId = UUID.randomUUID();

            // Generate a unique name for the pdf file
            String pdfName = empleadorId + "-" + pdfFile.getOriginalFilename();

            // Upload the pdf file to Minion
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minionBucketName)
                            .object(pdfName)
                            .stream(pdfFile.getInputStream(), pdfFile.getSize(), -1)
                            .contentType(pdfFile.getContentType())
                            .build());

            // Generate the pdf url
            String pdfUrl = minionEndpoint + "/" + minionBucketName + "/" + pdfName;

            // Procesar el PDF con Tesseract
            String ocrResult = procesarPDF(pdfFile.getInputStream());

            // Loguear el resultado del OCR (puedes ajustarlo según tus necesidades)
            Logger.getLogger(EmpleadorService.class.getName()).info("Texto extraído del PDF: " + ocrResult);

            // Create a new user object
            Empleador empleador = new Empleador(empleadorId, nombre, apellido, pdfUrl, ocrResult);

            // Save the user to Cassandra
            empleadorRepository.save(empleador);

            // Return the user
            return empleador;
        } catch (Exception e) {
            System.err.println("Error al procesar y guardar el empleador: " + e.getMessage());
            throw e;
        }
    }

    private String procesarPDF(InputStream pdfStream) throws Exception {
        try {
            // Crear un archivo temporal
            Path tempPdfPath = Files.createTempFile("temp-pdf", ".pdf");

            // Escribir el contenido del InputStream al archivo temporal
            Files.copy(pdfStream, tempPdfPath, StandardCopyOption.REPLACE_EXISTING);

            // Convertir Path a File
            File pdfFile = tempPdfPath.toFile();

            // Realizar OCR con Tesseract
            String ocrResult = tesseract.doOCR(pdfFile);

            // Convertir la cadena a la codificación del sistema
            byte[] bytes = ocrResult.getBytes(StandardCharsets.UTF_8);
            ocrResult = new String(bytes, Charset.defaultCharset());

            // Configuración de Hazelcast IMDG
            // Configuración de Hazelcast IMDG
            Config config = new Config();
            config.getJetConfig().setEnabled(true);
            HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);

// Creación del Pipeline
            Pipeline pipeline = Pipeline.create();

// Fuente de datos: Un solo string en formato "clave:valor"
            BatchStage<AbstractMap.SimpleEntry<String, String>> jsonEntries = pipeline.readFrom(Sources.<String>list("sourceList"))
                    .map(entry -> {
                        // Dividir la entrada en clave y valor, teniendo en cuenta el salto de línea
                        String[] parts = entry.split("\n");
                        StringBuilder json = new StringBuilder("{");

                        for (String part : parts) {
                            String[] keyValue = part.split(":");
                            if (keyValue.length == 2) {
                                String key = keyValue[0].trim();
                                String value = keyValue[1].trim();
                                // Agregar al JSON
                                json.append(String.format("\"%s\":\"%s\",", key, value));
                            }
                        }

                        // Eliminar la última coma si existe y cerrar el JSON
                        if (json.charAt(json.length() - 1) == ',') {
                            json.deleteCharAt(json.length() - 1);
                        }

                        json.append("}");
                        return new AbstractMap.SimpleEntry<>(entry, json.toString());
                    })
                    .setName("Map String to JSON Object")
                    .setLocalParallelism(1);

            jsonEntries.peek()
                    .writeTo(Sinks.observable("results"));

            jsonEntries.peek()
                    .writeTo(Sinks.logger());

            jsonEntries
                    .writeTo(Sinks.map("jsonMap"));

// Iniciar el Job
            hz.getJet().newJob(pipeline);

// Alimentar la fuente con un solo string de ejemplo
            hz.getList("sourceList").add(ocrResult);


            // Eliminar el archivo temporal
            Files.delete(tempPdfPath);

            return ocrResult;
        } catch (Exception e) {
            System.err.println("Error al procesar el PDF con Tesseract: " + e.getMessage());
            throw e;
        }
    }
}
