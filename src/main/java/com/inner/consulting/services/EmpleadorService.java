package com.inner.consulting.services;

import com.inner.consulting.config.KafkaConfig;
import com.inner.consulting.repositories.EmpleadorRepository;
import com.inner.consulting.entities.Empleador;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.sourceforge.tess4j.ITesseract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.kafka.KafkaSinks;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.BatchStage;
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
    @Autowired
    private KafkaConfig kafkaConfig;
    @Value("${minion.endpoint}")
    private String minionEndpoint;

    @Value("${minion.bucketName}")
    private String minionBucketName;

    public Empleador saveEmpleador(String nombre, String apellido, MultipartFile pdfFile) throws Exception {
        try {
            UUID empleadorId = UUID.randomUUID();
            String pdfName = empleadorId + "-" + pdfFile.getOriginalFilename();
            // subir archivos a minion
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minionBucketName)
                            .object(pdfName)
                            .stream(pdfFile.getInputStream(), pdfFile.getSize(), -1)
                            .contentType(pdfFile.getContentType())
                            .build());
            // generar la url del pdf
            String pdfUrl = minionEndpoint + "/" + minionBucketName + "/" + pdfName;
            // Procesar el PDF con Tesseract
            String ocrResult = procesarPDF(pdfFile.getInputStream());
            Logger.getLogger(EmpleadorService.class.getName()).info("Texto extraído del PDF: " + ocrResult);
            Empleador empleador = new Empleador(empleadorId, nombre, apellido, pdfUrl, ocrResult);
            empleadorRepository.save(empleador);
            return empleador;
        } catch (Exception e) {
           // System.err.println("Error al procesar y guardar el empleador: " + e.getMessage());
            Logger.getLogger("Error al procesar y guardar el empleador: " + e.getMessage());
            throw e;
        }
    }

    private String procesarPDF(InputStream pdfStream) throws Exception {
        try {
            // Crear un archivo temporal
            Path tempPdfPath = Files.createTempFile("temp-pdf", ".pdf");
            // escribir el contenido del InputStream al archivo temporal
            Files.copy(pdfStream, tempPdfPath, StandardCopyOption.REPLACE_EXISTING);
            // Convertir  el Path a File
            File pdfFile = tempPdfPath.toFile();
            // extraccion del texto del pdf  con Tesseract
            String ocrResult = tesseract.doOCR(pdfFile);
            // Convertir la cadena a la codificación del sistema
            byte[] bytes = ocrResult.getBytes(StandardCharsets.UTF_8);
            ocrResult = new String(bytes, Charset.defaultCharset());
            // configuración de Hazelcast IMDG
            Config config = new Config();
            config.getJetConfig().setEnabled(true);
            HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
            // Creación del Pipeline
            Pipeline pipeline = Pipeline.create();
            // transformacion de texto extraido a formato json
            BatchStage<AbstractMap.SimpleEntry<String, String>> jsonEntries = pipeline
                    .readFrom(Sources.<String>list("sourceList"))
                    .map(entry -> {
                        String[] parts = entry.split("\n");
                        StringBuilder json = new StringBuilder("{");
                        for (String part : parts) {
                            String[] keyValue = part.split(":");
                            if (keyValue.length == 2) {
                                String key = keyValue[0].trim();
                                String value = keyValue[1].trim();
                                json.append(String.format("\"%s\":\"%s\",", key, value));
                            }
                        }
                        if (json.charAt(json.length() - 1) == ',') {
                            json.deleteCharAt(json.length() - 1);
                        }
                        json.append("}");
                        return new AbstractMap.SimpleEntry<>(entry, json.toString());
                    })
                    .setName("Map String to JSON Object")
                    .setLocalParallelism(1);
            Properties props = kafkaConfig.producerProperties();
            // Properties props = new Properties();
            // props.setProperty("bootstrap.servers", "localhost:9092");
            // props.setProperty("key.serializer",
            // StringSerializer.class.getCanonicalName());
            // props.setProperty("value.serializer",
            // StringSerializer.class.getCanonicalName());
            jsonEntries
                    .writeTo(KafkaSinks.kafka(props, "topic1"));
            jsonEntries.peek()
                    .writeTo(Sinks.observable("results"));
            jsonEntries.peek()
                    .writeTo(Sinks.logger());
            jsonEntries
                    .writeTo(Sinks.map("jsonMap"));
            // iniciar el Job en hazelcast
            hz.getJet().newJob(pipeline);
            // alimentar la fuente de datos
            hz.getList("sourceList").add(ocrResult);
            Files.delete(tempPdfPath);
            return ocrResult;
        } catch (Exception e) {
           // System.err.println("Error al procesar el PDF con Tesseract: " + e.getMessage());
                Logger.getLogger("Error al procesar el PDF con Tesseract: " + e.getMessage());
            throw e;
        }
    }
}
