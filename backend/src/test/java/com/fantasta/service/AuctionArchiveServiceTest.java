package com.fantasta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class AuctionArchiveServiceTest {
    @Inject AuctionArchiveService service;

    @Test
    void developmentEnvironmentCannotPublish() {
        assertFalse(service.canPublish());
    }
}
