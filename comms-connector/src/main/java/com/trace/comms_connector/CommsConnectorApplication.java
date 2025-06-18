package com.trace.comms_connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class CommsConnectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommsConnectorApplication.class, args);
	}

    @EventListener(ApplicationReadyEvent.class)
    public void runCommsThreadOnStartup() {
        CommsThread thread = new CommsThread();
		thread.start();
    }
}
