package com.academia.banco.config;

import com.academia.banco.batch.MovimientoProcessor;
import com.academia.banco.model.Movimiento;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class CierreJobConfig {

    // Tasklet: revisa que exista el archivo de movimientos de la fecha que recibió el Job.
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

    // El Lector: lee el archivo de la fecha del Job, un renglón a la vez, y lo convierte en un Movimiento.
    @Bean
    @StepScope
    public FlatFileItemReader<Movimiento> movimientoReader(@Value("#{jobParameters['fecha']}") String fecha) {
        return new FlatFileItemReaderBuilder<Movimiento>()
                .name("movimientoReader")
                .resource(new FileSystemResource("datos/movimientos-" + fecha + ".csv"))
                .linesToSkip(1)                          // el encabezado
                .delimited()                             // separado por comas
                .names("cuenta", "tipo", "monto")        // las columnas, en orden
                .targetType(Movimiento.class)            // cada renglón → un Movimiento
                .build();
    }

    // El Escritor: guarda en MySQL los movimientos que le llegan, todos juntos.
    @Bean
    public JdbcBatchItemWriter<Movimiento> movimientoWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Movimiento>()
                .dataSource(dataSource)
                .sql("INSERT INTO movimiento (cuenta, tipo, monto) VALUES (:cuenta, :tipo, :monto)")
                .beanMapped()                            // :cuenta → movimiento.cuenta(), etc.
                .build();
    }

    // Un Step de tipo Chunk: lee, procesa y escribe de 10 en 10.
    @Bean
    public Step cargarMovimientosStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<Movimiento> movimientoReader,
                                      JdbcBatchItemWriter<Movimiento> movimientoWriter) {
        return new StepBuilder("cargarMovimientosStep", jobRepository)
                .<Movimiento, Movimiento>chunk(10)
                .transactionManager(transactionManager)
                .reader(movimientoReader)
                .processor(new MovimientoProcessor())
                .writer(movimientoWriter)
                .build();
    }


    // Tasklet: consulta la tabla y resume cuántos movimientos hay y cuánto suman.
    @Bean
    public Step resumenStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            JdbcTemplate jdbcTemplate) {
        return new StepBuilder("resumenStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movimiento", Long.class);
                    BigDecimal suma = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(SUM(monto), 0) FROM movimiento", BigDecimal.class);
                    System.out.println(">>> Resumen: " + total + " movimientos en la tabla, suma de montos = " + suma);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // El Job: primero revisa que llegó el archivo, después lo carga.
    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step verificarArchivoStep, Step cargarMovimientosStep, Step resumenStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(verificarArchivoStep)
                .next(cargarMovimientosStep)
                .next(resumenStep)
                .build();
    }
}