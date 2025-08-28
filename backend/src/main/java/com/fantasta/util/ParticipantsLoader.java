package com.fantasta.util;

import com.fantasta.model.ParticipantEntity;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@ApplicationScoped
@Startup
public class ParticipantsLoader {

    @PostConstruct
    @Transactional
    public void init() {
        try {
            int def = defaultCredits();
            int before = (int) ParticipantEntity.count();
            int added = loadFromClasspath(def);
            Log.infof("ParticipantsLoader: before=%d, added=%d", before, added);
        } catch (Exception e) {
            Log.error("ParticipantsLoader: failed", e);
        }
    }

    @Transactional
    public int loadFromClasspath(int defaultCredits) throws Exception {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("participants.txt");
        if (is == null) {
            Log.warn("participants.txt not found on classpath");
            return 0;
        }
        int added = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                ParticipantEntity exists = ParticipantEntity.<ParticipantEntity>
                        find("lower(name)=?1", name.toLowerCase()).firstResult();
                if (exists == null) {
                    ParticipantEntity p = new ParticipantEntity();
                    p.name = name;
                    p.totalCredits = defaultCredits;
                    p.persist();
                    added++;
                }
            }
        }
        return added;
    }

    private int defaultCredits() {
        try {
            return Integer.parseInt(System.getProperty("app.credits.total",
                    System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));
        } catch (Exception e) { return 500; }
    }
}
