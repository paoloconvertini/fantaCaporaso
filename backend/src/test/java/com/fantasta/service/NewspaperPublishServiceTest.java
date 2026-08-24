package com.fantasta.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewspaperPublishServiceTest {
    @Test
    void publicationIsAlwaysDisabledOutsideProduction() {
        NewspaperPublishService service = configured("dev", "fantacaporaso-gazzetta");
        assertFalse(service.status().enabled());
    }

    @Test
    void mainWebsiteProjectCanNeverBeUsedForNewspaper() {
        NewspaperPublishService service = configured("prod", "fantacaporaso");
        assertFalse(service.status().enabled());
    }

    @Test
    void dedicatedProjectCanBeEnabledExplicitlyInProduction() {
        NewspaperPublishService service = configured("prod", "fantacaporaso-gazzetta");
        assertTrue(service.status().enabled());
    }

    private NewspaperPublishService configured(String environment, String project) {
        NewspaperPublishService service = new NewspaperPublishService();
        service.environment = environment;
        service.publishEnabled = true;
        service.accountId = Optional.of("account");
        service.apiToken = Optional.of("token");
        service.projectName = Optional.of(project);
        return service;
    }
}
