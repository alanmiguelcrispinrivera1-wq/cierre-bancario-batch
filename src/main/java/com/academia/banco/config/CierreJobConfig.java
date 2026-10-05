package com.academia.banco.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CierreJobConfig {

    // Un Step de tipo Tasklet: hace UNA tarea y termina.
    @Bean
    public Step saludoStep(JobRepository jobRepository) {
        return new StepBuilder("saludoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    System.out.println(">>> Hola desde el cierre del día");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    // Otro Tasklet: revisa que exista el archivo de movimientos de la fecha que recibió el Job.
    @Bean
    public Step verificarArchivoStep(JobRepository jobRepository) {
        return new StepBuilder("verificarArchivoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object fecha = chunkContext.getStepContext().getJobParameters().get("fecha");
                    Path archivo = Path.of("datos/movimientos-" + fecha + ".csv");
                    if (!Files.exists(archivo)) {
                        throw new IllegalStateException("No existe el archivo del día: " + archivo);
                    }
                    long movimientos = Files.readAllLines(archivo).size() - 1;   // menos el encabezado
                    System.out.println(">>> Archivo del día: " + archivo + " (" + movimientos + " movimientos)");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    // Tercer Tasklet: cuenta cuántos archivos hay en la carpeta datos/.
    @Bean
    public Step contarArchivosStep(JobRepository jobRepository) {
        return new StepBuilder("contarArchivosStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    long archivos;
                    try (var lista = Files.list(Path.of("datos"))) {   // el try cierra el listado al terminar
                        archivos = lista.count();
                    }
                    System.out.println(">>> Archivos en datos/: " + archivos);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    // El Job: el contenedor de los steps. Primero el saludo, después la revisión del archivo y al final el conteo de archivos
    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step saludoStep, Step verificarArchivoStep, Step contarArchivosStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(saludoStep)
                .next(verificarArchivoStep)
                .next(contarArchivosStep)
                .build();
    }
}