package com.blooddonation.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Real-world: blood expires. Nightly job auto-discards expired available units
 * so availability counts, dashboards and dispatch decisions stay truthful
 * without manual QA cleanup.
 */
@Component
public class BloodExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BloodExpiryScheduler.class);

    @Autowired
    private BloodDonationService service;

    // Every day at 02:00 server time
    @Scheduled(cron = "0 0 2 * * *")
    public void discardExpired() {
        try {
            int n = service.autoDiscardExpiredUnits();
            log.info("Nightly expiry sweep complete, discarded={}", n);
        } catch (Exception e) {
            log.error("Nightly expiry sweep failed: {}", e.getMessage(), e);
        }
    }
}
