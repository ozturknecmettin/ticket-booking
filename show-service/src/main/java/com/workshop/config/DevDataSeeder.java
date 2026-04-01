package com.workshop.config;

import com.workshop.commands.ShowCommands;
import com.workshop.projection.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private final CommandGateway commandGateway;
    private final ShowRepository showRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Wait briefly for tracking event processor to replay existing events from Axon Server
        Thread.sleep(3000);

        log.info("[DevDataSeeder] Seeding demo shows...");
        if (!showRepository.existsById("show-001")) {
            sendWithRetry(new ShowCommands.CreateShow("show-001", "Hamilton", "Fortune Theatre, London", 432, new BigDecimal("75.00"),
                    LocalDateTime.of(2026, 4, 15, 19, 30), "THEATRE", "Original Broadway Cast", "The story of America's Founding Father Alexander Hamilton, an immigrant from the Caribbean who became George Washington's right-hand man.", 165, null));
        } else {
            log.info("[DevDataSeeder] show-001 already present — skipping.");
        }
        if (!showRepository.existsById("show-002")) {
            sendWithRetry(new ShowCommands.CreateShow("show-002", "The Lion King", "Harold Pinter Theatre, London", 578, new BigDecimal("95.00"),
                    LocalDateTime.of(2026, 5, 20, 18, 0), "THEATRE", "West End Production", "Disney's beloved classic comes to life in a spectacular stage adaptation featuring breathtaking costumes and puppetry.", 150, null));
        } else {
            log.info("[DevDataSeeder] show-002 already present — skipping.");
        }
        if (!showRepository.existsById("show-003")) {
            sendWithRetry(new ShowCommands.CreateShow("show-003", "Phantom of the Opera", "Trafalgar Theatre, London", 800, new BigDecimal("85.00"),
                    LocalDateTime.of(2026, 6, 10, 20, 0), "THEATRE", "Andrew Lloyd Webber", "The world's longest-running show — a passionate story of a disfigured musical genius who haunts the Paris Opera House.", 145, null));
        } else {
            log.info("[DevDataSeeder] show-003 already present — skipping.");
        }
        log.info("[DevDataSeeder] Done — seed complete for show-001, show-002, show-003");
    }

    private void sendWithRetry(Object command) throws InterruptedException {
        int attempts = 0;
        while (true) {
            try {
                commandGateway.sendAndWait(command);
                return;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                // Aggregate already exists in event store (e.g. after service restart with Axon Server)
                if (msg.contains("Invalid sequence number") || msg.contains("AXONIQ-2000")) {
                    log.info("[DevDataSeeder] Aggregate already exists in event store — skipping: {}", msg);
                    return;
                }
                if (++attempts >= 10) throw e;
                log.warn("[DevDataSeeder] Command dispatch not ready yet, retrying in 2s... (attempt {}/10)", attempts);
                Thread.sleep(2000);
            }
        }
    }
}
