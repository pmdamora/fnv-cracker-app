package passwordcrack;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.springframework.scheduling.annotation.EnableAsync;
import passwordcrack.storage.FileStorageProperties;
import passwordcrack.storage.StorageService;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({FileStorageProperties.class})
public class Application {

    /**
     * Starts the Spring Application
     * @param args None
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }


    /**
     * Initiates the Storage service
     * @param storageService storage service interface
     * @return CommandLineRunner
     */
    @Bean
    CommandLineRunner init(StorageService storageService) {
        return (args) -> {
            storageService.deleteAll();
            storageService.init();
        };
    }
}