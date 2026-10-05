package com.academia.banco.config;

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

    // El Job: el contenedor de los steps. Por ahora tiene uno.
    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step saludoStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(saludoStep)
                .build();
    }
}