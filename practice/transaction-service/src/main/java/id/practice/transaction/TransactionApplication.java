package id.practice.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"id.practice.transaction", "id.practice.common"})
public class TransactionApplication {
    public static void main(String[] args) { SpringApplication.run(TransactionApplication.class, args); }
}