package dev.toleflaco.erp_purchasing_agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ErpPurchasingAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(ErpPurchasingAgentApplication.class, args);
	}

}
