package com.fantasta.service;

import com.fantasta.dto.ParticipantRosterDto;
import com.fantasta.dto.RosterDto;
import com.fantasta.dto.RosterImportResult;
import com.fantasta.dto.SvincoloRequest;
import com.fantasta.dto.MarketRosterImportResult;
import com.fantasta.dto.RosterSwapRequest;
import com.fantasta.dto.RosterSwapResult;
import com.fantasta.model.*;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class RosterService {

    private static final String FANTAMASTER_ROSTERS_TEMPLATE =
            "fantamaster/rose_lega_1590336.xlsx";
    @Inject
    SecurityIdentity identity;

    @Inject
    ParticipantService participantService;

    @Inject
    MercatoService mercatoService;

    @Inject
    MarketRestrictionService marketRestrictionService;

    /** Massimali per ruolo, letti da properties/env (default: 3-8-8-6) */
    public int max(Role role) {
        String v;
        switch (role) {
            case PORTIERE ->
                    v = System.getProperty("app.roster.portieri",
                            System.getenv().getOrDefault("APP_ROSTER_PORTIERI", "3"));
            case DIFENSORE ->
                    v = System.getProperty("app.roster.difensori",
                            System.getenv().getOrDefault("APP_ROSTER_DIFENSORI", "8"));
            case CENTROCAMPISTA ->
                    v = System.getProperty("app.roster.centrocampisti",
                            System.getenv().getOrDefault("APP_ROSTER_CENTROCAMPISTI", "8"));
            default ->
                    v = System.getProperty("app.roster.attaccanti",
                            System.getenv().getOrDefault("APP_ROSTER_ATTACCANTI", "6"));
        }
        try { return Integer.parseInt(v); } catch (Exception e) { return 0; }
    }

    /** Ritorna i conteggi attuali per ruolo (deriva da RosterEntity) */
    public Map<Role, Integer> roleCounts(Long participantId) {
        return participantService.roleCounts(participantId);
    }

    /** Massimo spendibile conservando un credito per ogni posto che restera' vuoto. */
    public int maxBid(Long participantId, int totalCredits, int purchaseSize) {
        int remainingCredits = participantService.remainingCreditsById(participantId, totalCredits);
        Map<Role, Integer> counts = participantService.roleCounts(participantId);
        int reservedCredits = reservedCreditsForBid(counts, purchaseSize);
        return Math.max(0, remainingCredits - reservedCredits);
    }

    int reservedCreditsForBid(Map<Role, Integer> counts, int purchaseSize) {
        return Math.max(0, reservedCreditsForCurrentOpenSlots(counts) - Math.max(1, purchaseSize));
    }

    int reservedCreditsForCurrentOpenSlots(Map<Role, Integer> counts) {
        int remainingSlots = 0;
        for (Role role : Role.values()) {
            int count = counts.getOrDefault(role, 0);
            // La porta viene acquistata come pacchetto: se almeno un portiere e'
            // presente, eventuali record mancanti non sono acquisti da finanziare.
            if (role == Role.PORTIERE && count > 0) {
                count = max(role);
            }
            remainingSlots += Math.max(0, max(role) - count);
        }
        return remainingSlots;
    }

    @Transactional
    public synchronized RosterSwapResult swap(RosterSwapRequest request) {
        if (request == null || request.sourceParticipantId == null || request.destinationParticipantId == null
                || request.sourcePlayerIds == null || request.destinationPlayerIds == null) {
            throw new BadRequestException("Dati dello scambio incompleti");
        }
        if (Objects.equals(request.sourceParticipantId, request.destinationParticipantId)) {
            throw new BadRequestException("Seleziona due fantasquadre differenti");
        }
        List<Long> sourceIds = request.sourcePlayerIds.stream().distinct().toList();
        List<Long> destinationIds = request.destinationPlayerIds.stream().distinct().toList();
        if (sourceIds.isEmpty() || sourceIds.size() != request.sourcePlayerIds.size()
                || destinationIds.size() != request.destinationPlayerIds.size()
                || sourceIds.size() != destinationIds.size()) {
            throw new BadRequestException("Lo scambio deve contenere lo stesso numero di calciatori per entrambe le squadre");
        }

        ParticipantEntity source = ParticipantEntity.findById(request.sourceParticipantId);
        ParticipantEntity destination = ParticipantEntity.findById(request.destinationParticipantId);
        if (source == null || destination == null) throw new BadRequestException("Fantasquadra non trovata");

        List<RosterEntity> sourceEntries = rosterEntries(source, sourceIds);
        List<RosterEntity> destinationEntries = rosterEntries(destination, destinationIds);
        validateGoalkeeperSwap(source, destination, sourceEntries, destinationEntries);
        validateFinalRoleCounts(source, sourceEntries, destinationEntries);
        validateFinalRoleCounts(destination, destinationEntries, sourceEntries);

        int sourceRemaining = participantService.remainingCreditsById(source.id, source.totalCredits);
        int destinationRemaining = participantService.remainingCreditsById(destination.id, destination.totalCredits);
        String operationCode = UUID.randomUUID().toString();

        sourceEntries.forEach(entry -> transferForSwap(entry, source, destination, operationCode));
        destinationEntries.forEach(entry -> transferForSwap(entry, destination, source, operationCode));

        source.totalCredits = sourceRemaining + participantService.spentCreditsById(source.id);
        destination.totalCredits = destinationRemaining + participantService.spentCreditsById(destination.id);

        return new RosterSwapResult(
                sourceEntries.stream().map(entry -> entry.player.name).toList(),
                destinationEntries.stream().map(entry -> entry.player.name).toList(),
                sourceRemaining,
                destinationRemaining);
    }

    private List<RosterEntity> rosterEntries(ParticipantEntity owner, List<Long> playerIds) {
        List<RosterEntity> entries = RosterEntity.<RosterEntity>list(
                "participant = ?1 and player.id in ?2", owner, playerIds);
        if (entries.size() != playerIds.size()) {
            throw new BadRequestException("La rosa è cambiata: ricarica la pagina e ripeti lo scambio");
        }
        return entries;
    }

    private void validateGoalkeeperSwap(ParticipantEntity source, ParticipantEntity destination,
                                        List<RosterEntity> sourceEntries, List<RosterEntity> destinationEntries) {
        boolean sourceGoalkeepers = sourceEntries.stream().anyMatch(entry -> entry.player.role == Role.PORTIERE);
        boolean destinationGoalkeepers = destinationEntries.stream().anyMatch(entry -> entry.player.role == Role.PORTIERE);
        if (!sourceGoalkeepers && !destinationGoalkeepers) return;
        boolean onlyGoalkeepers = sourceEntries.stream().allMatch(entry -> entry.player.role == Role.PORTIERE)
                && destinationEntries.stream().allMatch(entry -> entry.player.role == Role.PORTIERE);
        long sourcePackageSize = RosterEntity.count("participant = ?1 and player.role = ?2", source, Role.PORTIERE);
        long destinationPackageSize = RosterEntity.count("participant = ?1 and player.role = ?2", destination, Role.PORTIERE);
        if (!onlyGoalkeepers || sourceEntries.size() != sourcePackageSize
                || destinationEntries.size() != destinationPackageSize) {
            throw new BadRequestException("I portieri possono essere scambiati soltanto come pacchetto completo contro pacchetto completo");
        }
    }

    private void validateFinalRoleCounts(ParticipantEntity participant, List<RosterEntity> outgoing,
                                         List<RosterEntity> incoming) {
        Map<Role, Integer> counts = new EnumMap<>(participantService.roleCounts(participant.id));
        outgoing.forEach(entry -> counts.merge(entry.player.role, -1, Integer::sum));
        incoming.forEach(entry -> counts.merge(entry.player.role, 1, Integer::sum));
        for (Role role : Role.values()) {
            if (counts.getOrDefault(role, 0) > max(role)) {
                throw new BadRequestException(participant.name + ": quota piena per ruolo " + role.name());
            }
        }
    }

    private void transferForSwap(RosterEntity entry, ParticipantEntity source,
                                 ParticipantEntity destination, String operationCode) {
        PlayerOwnerHistoryEntity.remember(entry.player, source);
        PlayerOwnerHistoryEntity.remember(entry.player, destination);
        MarketMovementEntity movement = new MarketMovementEntity();
        movement.participant = source;
        movement.destinationParticipant = destination;
        movement.player = entry.player;
        movement.type = MarketMovementEntity.Type.EXCHANGE;
        movement.currentValue = entry.player.valore == null ? 0D : entry.player.valore;
        movement.previousRosterAmount = entry.amount == null ? 0D : entry.amount;
        movement.refundedAmount = 0D;
        movement.playerNameSnapshot = entry.player.name;
        movement.playerTeamSnapshot = entry.player.team;
        movement.sourceParticipantSnapshot = source.name;
        movement.destinationParticipantSnapshot = destination.name;
        movement.operationCode = operationCode;
        movement.resultingRosterAmount = entry.amount;
        movement.creditsPreserved = true;
        var market = mercatoService.getConfig();
        movement.sessionCode = market == null ? null : market.sessionCode;
        movement.persist();
        entry.participant = destination;
    }

    /** Posti rosa ancora vuoti, sommati su tutti i partecipanti e divisi per ruolo. */
    public Map<String, Integer> openSlotsByRole() {
        int participants = Math.toIntExact(ParticipantEntity.count());
        Map<String, Integer> result = new LinkedHashMap<>();
        int total = 0;
        for (Role role : Role.values()) {
            int assigned = Math.toIntExact(RosterEntity.count("player.role", role));
            int open = Math.max(0, participants * max(role) - assigned);
            result.put(role.name(), open);
            total += open;
        }
        result.put("TUTTI", total);
        return result;
    }

    @Transactional
    public RosterImportResult importFromExcel(InputStream in, boolean confirm) {
        List<String> errors = new ArrayList<>();
        int inserted = 0;
        int teamsCreated = 0;
        int teamsFound = 0;
        int defaultCredits = Integer.parseInt(System.getProperty("app.credits.total",
                System.getenv().getOrDefault("APP_CREDITS_TOTAL", "500")));

        try (Workbook workbook = WorkbookFactory.create(in)) {
            Set<Long> assignedNow = new HashSet<>();
            Set<Long> resetParticipants = new HashSet<>();

            for (Sheet sheet : workbook) {
                String participantName = templateTeamName(cellText(sheet.getRow(0), 0));
                if (participantName.isBlank()) {
                    errors.add("Foglio senza nome squadra: " + sheet.getSheetName());
                    continue;
                }
                teamsFound++;
                ParticipantEntity participant = ParticipantEntity.find("lower(name) = ?1", participantName.toLowerCase(Locale.ROOT)).firstResult();
                if (participant == null) {
                    teamsCreated++;
                    if (confirm) {
                        participant = new ParticipantEntity();
                        participant.name = participantName;
                        participant.totalCredits = defaultCredits;
                        participant.persist();
                    }
                }
                if (confirm && resetParticipants.add(participant.id)) {
                    copyRosterToHistory(participant);
                    RosterEntity.delete("participant", participant);
                }
                for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    String playerName = cellText(row, 0);
                    if (playerName.isBlank() || playerName.startsWith("Ultimo aggiornamento:") || playerName.equalsIgnoreCase("Scarica FantaMaster")) continue;
                    String team = cellText(row, 1);
                    double amount = numericCell(row, 3);
                    PlayerEntity player = PlayerEntity.find("lower(name) = ?1 and lower(team) = ?2",
                            playerName.toLowerCase(Locale.ROOT), team.toLowerCase(Locale.ROOT)).firstResult();
                    if (player == null) {
                        errors.add("Giocatore non trovato: " + playerName + " (" + team + ")");
                        continue;
                    }
                    inserted++;
                    if (!confirm) continue;
                    RosterEntity roster = new RosterEntity();
                    roster.participant = participant;
                    roster.player = player;
                    roster.amount = amount;
                    roster.persist();
                    player.assigned = true;
                    assignedNow.add(player.id);
                }
            }

            if (confirm && !assignedNow.isEmpty()) {
                PlayerEntity.update("assigned = false WHERE assigned = true AND id NOT IN ?1", assignedNow);
            } else if (confirm) {
                PlayerEntity.update("assigned = false WHERE assigned = true");
            }
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'import da Excel: " + e.getMessage(), e);
        }
        return new RosterImportResult(inserted, errors, teamsFound, teamsCreated, !confirm);
    }

    /**
     * Confronta una rosa post-scambi con quella corrente. In riparazione sono
     * consentiti soltanto scambi tra proprietari esistenti e rimozioni.
     */
    @Transactional
    public MarketRosterImportResult reconcileMarketRosters(InputStream in, boolean confirm) {
        mercatoService.requireUpdatedQuotes();
        Map<Long, ParticipantEntity> desiredOwnerByPlayer = new HashMap<>();
        Map<Long, Double> historicalCosts = new HashMap<>();
        Set<Long> seenParticipants = new HashSet<>();
        MarketRosterImportResult result = new MarketRosterImportResult(!confirm);

        try (Workbook workbook = WorkbookFactory.create(in)) {
            for (Sheet sheet : workbook) {
                String participantName = templateTeamName(cellText(sheet.getRow(0), 0));
                if (participantName.isBlank()) continue;
                ParticipantEntity participant = ParticipantEntity.find(
                        "lower(name) = ?1", participantName.toLowerCase(Locale.ROOT)).firstResult();
                if (participant == null) {
                    result.errors.add("Squadra non riconosciuta: " + participantName);
                    continue;
                }
                seenParticipants.add(participant.id);
                for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    String playerName = cellText(row, 0);
                    if (playerName.isBlank() || playerName.startsWith("Ultimo aggiornamento:")
                            || playerName.equalsIgnoreCase("Scarica FantaMaster")) continue;
                    String team = cellText(row, 1);
                    PlayerEntity player = PlayerEntity.find(
                            "lower(name) = ?1 and lower(team) = ?2",
                            playerName.toLowerCase(Locale.ROOT), team.toLowerCase(Locale.ROOT)).firstResult();
                    if (player == null) {
                        result.errors.add("Calciatore non riconosciuto: " + playerName + " (" + team + ")");
                        continue;
                    }
                    ParticipantEntity duplicate = desiredOwnerByPlayer.putIfAbsent(player.id, participant);
                    String costText = cellText(row, 3).replace(',', '.');
                    try {
                        double cost = Double.parseDouble(costText);
                        if (Double.isFinite(cost) && cost >= 0) historicalCosts.put(player.id, cost);
                    } catch (NumberFormatException ignored) {
                        // Richiesto soltanto per ripristinare una riga mancante nel database.
                    }
                    if (duplicate != null) {
                        result.errors.add(player.name + " compare sia in " + duplicate.name + " sia in " + participant.name);
                    }
                }
            }
        } catch (Exception e) {
            throw new BadRequestException("File rose non valido: " + e.getMessage());
        }

        List<ParticipantEntity> allParticipants = ParticipantEntity.listAll();
        for (ParticipantEntity participant : allParticipants) {
            if (!seenParticipants.contains(participant.id)) {
                result.errors.add("Manca il foglio della squadra " + participant.name);
            }
        }

        List<RosterEntity> current = RosterEntity.listAll();
        Map<Long, RosterEntity> currentByPlayer = current.stream()
                .collect(Collectors.toMap(entry -> entry.player.id, entry -> entry));
        List<GoalkeeperCorrection> corrections = goalkeeperCorrections(current, desiredOwnerByPlayer, result);
        Set<Long> correctedOutgoing = corrections.stream().filter(c -> c.entry() != null).map(c -> c.entry().player.id).collect(Collectors.toSet());
        Set<Long> correctedIncoming = corrections.stream().map(c -> c.replacement().id).collect(Collectors.toSet());
        List<RosterEntity> missingEntries = new ArrayList<>();
        for (Map.Entry<Long, ParticipantEntity> desired : desiredOwnerByPlayer.entrySet()) {
            if (correctedIncoming.contains(desired.getKey())) continue;
            RosterEntity existing = currentByPlayer.get(desired.getKey());
            PlayerEntity player = PlayerEntity.findById(desired.getKey());
            if (existing == null) {
                long desiredCount = desiredOwnerByPlayer.entrySet().stream()
                        .filter(entry -> entry.getValue().id.equals(desired.getValue().id))
                        .map(entry -> PlayerEntity.<PlayerEntity>findById(entry.getKey()))
                        .filter(candidate -> candidate.role == player.role).count();
                if (player.role == Role.PORTIERE || !player.active || !historicalCosts.containsKey(player.id)
                        || desiredCount > max(player.role)) {
                    result.errors.add("Riga rosa mancante non ripristinabile: " + player.name
                            + " (verifica ruolo, disponibilità, costo storico nel file e posti in rosa)");
                } else {
                    RosterEntity restored = new RosterEntity();
                    restored.player = player;
                    restored.participant = desired.getValue();
                    restored.amount = historicalCosts.get(player.id);
                    missingEntries.add(restored);
                    result.rosterCorrections.add(player.name + " — " + restored.participant.name
                            + " (ripristino riga mancante, costo storico " + restored.amount + ", crediti invariati)");
                }
            } else if (Objects.equals(existing.participant.id, desired.getValue().id)) {
                result.unchanged.add(player.name + " — " + existing.participant.name);
            } else {
                result.exchanges.add(player.name + ": " + existing.participant.name + " → " + desired.getValue().name);
            }
        }
        for (RosterEntity existing : current) {
            if (correctedOutgoing.contains(existing.player.id)) continue;
            if (!desiredOwnerByPlayer.containsKey(existing.player.id)) {
                result.releases.add(existing.player.name + " — " + existing.participant.name
                        + " (rimborso " + Math.round(existing.player.valore == null ? 0D : existing.player.valore) + ")");
            }
        }

        validateGoalkeeperReconciliation(current.stream()
                .filter(entry -> !correctedOutgoing.contains(entry.player.id)).toList(), desiredOwnerByPlayer, result);
        if (!result.errors.isEmpty() || !confirm) return result;

        String operationCode = UUID.randomUUID().toString();

        Map<Long, Integer> remainingBeforeCorrections = new HashMap<>();
        for (ParticipantEntity participant : allParticipants) {
            remainingBeforeCorrections.put(participant.id,
                    participantService.remainingCreditsById(participant.id, participant.totalCredits));
        }
        for (RosterEntity entry : missingEntries) {
            entry.persist();
            entry.player.assigned = true;
            marketRestrictionService.rememberCurrentOwner(entry.player, entry.participant);
            Log.infof("Ripristino riga rosa per %s: %s, costo storico %s",
                    entry.participant.name, entry.player.name, entry.amount);
        }

        Set<PlayerEntity> correctedPlayers = new HashSet<>();
        for (GoalkeeperCorrection correction : corrections) {
            RosterEntity entry = correction.entry();
            if (entry == null) {
                entry = new RosterEntity();
                entry.participant = correction.owner();
                entry.amount = 0D; // Completamento di una porta già pagata, senza nuovo addebito.
                entry.player = correction.replacement();
                entry.persist();
            } else {
                PlayerEntity outgoing = entry.player;
                PlayerOwnerHistoryEntity.remember(outgoing, entry.participant);
                correctedPlayers.add(outgoing);
                entry.player = correction.replacement();
            }
            correctedPlayers.add(entry.player);
            marketRestrictionService.rememberCurrentOwner(entry.player, entry.participant);
            Log.infof("Correzione pacchetto portieri per %s: %s, costo riga %s, sessione %s",
                    entry.participant.name, entry.player.name, entry.amount,
                    mercatoService.requireConfiguredMarket().sessionCode);
        }
        for (PlayerEntity player : correctedPlayers) {
            player.assigned = RosterEntity.count("player", player) > 0;
        }
        for (ParticipantEntity participant : allParticipants) {
            participant.totalCredits = remainingBeforeCorrections.get(participant.id)
                    + participantService.spentCreditsById(participant.id);
        }

        // Le rimozioni passano dallo stesso servizio degli svincoli manuali.
        Set<Long> releasedPlayers = new HashSet<>();
        for (RosterEntity existing : new ArrayList<>(current)) {
            if (desiredOwnerByPlayer.containsKey(existing.player.id) || releasedPlayers.contains(existing.player.id)) continue;
            List<RosterEntity> releasedEntries = existing.player.role == Role.PORTIERE
                    ? RosterEntity.list("participant = ?1 and player.role = ?2", existing.participant, Role.PORTIERE)
                    : List.of(existing);
            releasedEntries.forEach(entry -> releasedPlayers.add(entry.player.id));
            SvincoloRequest request = new SvincoloRequest();
            request.playerId = existing.player.id;
            svincola(existing.participant.id, request, operationCode);
        }

        Map<Long, Integer> remainingBeforeExchanges = new HashMap<>();
        for (ParticipantEntity participant : allParticipants) {
            remainingBeforeExchanges.put(participant.id,
                    participantService.remainingCreditsById(participant.id, participant.totalCredits));
        }

        for (Map.Entry<Long, ParticipantEntity> desired : desiredOwnerByPlayer.entrySet()) {
            RosterEntity existing = RosterEntity.find("player.id", desired.getKey()).firstResult();
            if (existing == null || Objects.equals(existing.participant.id, desired.getValue().id)) continue;
            ParticipantEntity previous = existing.participant;
            PlayerOwnerHistoryEntity.remember(existing.player, previous);
            PlayerOwnerHistoryEntity.remember(existing.player, desired.getValue());
            MarketMovementEntity movement = new MarketMovementEntity();
            movement.participant = previous;
            movement.destinationParticipant = desired.getValue();
            movement.player = existing.player;
            movement.type = MarketMovementEntity.Type.EXCHANGE;
            movement.currentValue = existing.player.valore == null ? 0D : existing.player.valore;
            movement.previousRosterAmount = existing.amount == null ? 0D : existing.amount;
            movement.refundedAmount = 0D;
            movement.playerNameSnapshot = existing.player.name;
            movement.playerTeamSnapshot = existing.player.team;
            movement.sourceParticipantSnapshot = previous.name;
            movement.destinationParticipantSnapshot = desired.getValue().name;
            movement.sessionCode = mercatoService.requireConfiguredMarket().sessionCode;
            movement.operationCode = operationCode;
            movement.resultingRosterAmount = existing.amount;
            movement.creditsPreserved = true;
            movement.persist();
            existing.participant = desired.getValue();
        }

        // Gli scambi non cambiano mai i crediti residui, indipendentemente dai costi storici scambiati.
        for (ParticipantEntity participant : allParticipants) {
            int newSpent = participantService.spentCreditsById(participant.id);
            participant.totalCredits = remainingBeforeExchanges.get(participant.id) + newSpent;
        }
        return result;
    }

    private record GoalkeeperCorrection(RosterEntity entry, ParticipantEntity owner, PlayerEntity replacement) {}

    private List<GoalkeeperCorrection> goalkeeperCorrections(List<RosterEntity> current,
                                                              Map<Long, ParticipantEntity> desired,
                                                              MarketRosterImportResult result) {
        List<GoalkeeperCorrection> corrections = new ArrayList<>();
        Map<Long, List<RosterEntity>> packages = current.stream()
                .filter(entry -> entry.player.role == Role.PORTIERE)
                .collect(Collectors.groupingBy(entry -> entry.participant.id));
        Map<Long, RosterEntity> assigned = current.stream().collect(Collectors.toMap(entry -> entry.player.id, entry -> entry));
        for (List<RosterEntity> entries : packages.values()) {
            ParticipantEntity owner = entries.get(0).participant;
            List<PlayerEntity> desiredKeepers = desired.entrySet().stream()
                    .filter(entry -> entry.getValue().id.equals(owner.id))
                    .map(entry -> PlayerEntity.<PlayerEntity>findById(entry.getKey()))
                    .filter(player -> player.role == Role.PORTIERE).toList();
            List<RosterEntity> outgoing = entries.stream()
                    .filter(entry -> !desired.containsKey(entry.player.id) || !desired.get(entry.player.id).id.equals(owner.id))
                    .sorted(Comparator.<RosterEntity>comparingDouble(entry -> entry.amount == null ? 0D : entry.amount)
                            .reversed().thenComparing(entry -> entry.player.id)).toList();
            List<PlayerEntity> incoming = desiredKeepers.stream()
                    .filter(player -> !assigned.containsKey(player.id) || !assigned.get(player.id).participant.id.equals(owner.id))
                    .sorted(Comparator.<PlayerEntity>comparingDouble(player -> player.valore == null ? 0D : player.valore)
                            .reversed().thenComparing(player -> player.id)).toList();
            if (incoming.isEmpty()) continue;
            // Lo scambio completo fra proprietari segue la riconciliazione ordinaria.
            if (entries.size() == 3 && outgoing.size() == 3 && incoming.size() == 3
                    && incoming.stream().allMatch(player -> assigned.containsKey(player.id))
                    && incoming.stream().map(player -> assigned.get(player.id).participant.id).distinct().count() == 1) continue;
            Map<String, Long> clubs = entries.stream().collect(Collectors.groupingBy(
                    entry -> normalizeTeamName(entry.player.team), Collectors.counting()));
            long maximum = clubs.values().stream().mapToLong(Long::longValue).max().orElse(0);
            List<String> mainClubs = clubs.entrySet().stream().filter(entry -> entry.getValue() == maximum)
                    .map(Map.Entry::getKey).toList();
            if (entries.size() > 3 || desiredKeepers.size() != 3 || incoming.size() != outgoing.size() + 3 - entries.size()
                    || mainClubs.size() != 1 || mainClubs.get(0).isBlank()
                    || incoming.stream().anyMatch(player -> !player.active || !normalizeTeamName(player.team).equals(mainClubs.get(0)))) {
                result.errors.add("Correzione porta non riconosciuta per " + owner.name
                        + ": il pacchetto deve avere tre portieri e i nuovi portieri devono appartenere allo stesso club della porta esistente");
                continue;
            }
            for (int i = 0; i < incoming.size(); i++) {
                RosterEntity entry = i < outgoing.size() ? outgoing.get(i) : null;
                PlayerEntity replacement = incoming.get(i);
                corrections.add(new GoalkeeperCorrection(entry, owner, replacement));
                result.goalkeeperCorrections.add(owner.name + ": "
                        + (entry == null ? "posto mancante" : entry.player.name) + " → " + replacement.name
                        + " (costo storico porta invariato, crediti invariati, nessun cambio porta)");
            }
        }
        Set<Long> correctedOutgoing = corrections.stream().filter(c -> c.entry() != null)
                .map(c -> c.entry().player.id).collect(Collectors.toSet());
        for (GoalkeeperCorrection correction : corrections) {
            RosterEntity source = assigned.get(correction.replacement().id);
            if (source != null && !correctedOutgoing.contains(source.player.id)) {
                result.errors.add("Il portiere " + source.player.name + " appartiene a " + source.participant.name
                        + ": anche la porta di origine deve essere corretta nello stesso import");
            }
        }
        return corrections;
    }

    private void validateGoalkeeperReconciliation(List<RosterEntity> current,
                                                   Map<Long, ParticipantEntity> desired,
                                                   MarketRosterImportResult result) {
        Map<Long, List<RosterEntity>> packages = current.stream()
                .filter(entry -> entry.player.role == Role.PORTIERE)
                .collect(Collectors.groupingBy(entry -> entry.participant.id));
        for (List<RosterEntity> goalkeeperEntries : packages.values()) {
            Set<Long> targetOwners = goalkeeperEntries.stream()
                    .map(entry -> desired.get(entry.player.id))
                    .filter(Objects::nonNull)
                    .map(owner -> owner.id)
                    .collect(Collectors.toSet());
            long absent = goalkeeperEntries.stream().filter(entry -> !desired.containsKey(entry.player.id)).count();
            if ((absent > 0 && absent < goalkeeperEntries.size()) || targetOwners.size() > 1) {
                result.errors.add("Il pacchetto portieri di " + goalkeeperEntries.get(0).participant.name
                        + " deve restare unito, essere scambiato interamente o essere ceduto interamente");
            }
        }
    }

    @Transactional
    public byte[] exportFantaMaster() {
        InputStream template = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(FANTAMASTER_ROSTERS_TEMPLATE);
        if (template == null) {
            throw new IllegalStateException("Template rose FantaMaster non trovato");
        }

        try (template; Workbook workbook = WorkbookFactory.create(template); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<ParticipantEntity> participants = ParticipantEntity.list("order by name");
            Map<String, ParticipantEntity> participantsByName = participants.stream()
                    .collect(Collectors.toMap(p -> normalizeTeamName(p.name), p -> p));
            Set<String> templateTeams = new LinkedHashSet<>();

            for (Sheet sheet : workbook) {
                String teamName = templateTeamName(cellText(sheet.getRow(0), 0));
                String normalizedTeamName = normalizeTeamName(teamName);
                templateTeams.add(normalizedTeamName);
                ParticipantEntity participant = participantsByName.get(normalizedTeamName);
                if (participant == null) {
                    throw new IllegalStateException("Il template FantaMaster contiene una squadra non presente: " + teamName);
                }

                List<RosterEntity> roster = RosterEntity.list("participant = ?1 order by player.role, player.name", participant);
                int rowIndex = 2;
                for (RosterEntity entry : roster) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(entry.player.name);
                    row.createCell(1).setCellValue(entry.player.team);
                    row.createCell(2).setCellValue(fantaMasterRole(entry.player.role));
                    row.createCell(3).setCellValue(entry.amount == null ? 0 : entry.amount);
                }
            }

            List<String> missingTeams = participants.stream()
                    .filter(p -> !templateTeams.contains(normalizeTeamName(p.name)))
                    .map(p -> p.name)
                    .toList();
            if (!missingTeams.isEmpty()) {
                throw new IllegalStateException("Squadre senza foglio nel template FantaMaster: "
                        + String.join(", ", missingTeams));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile esportare le rose", e);
        }
    }

    private String templateTeamName(String value) {
        if (value == null) return "";
        return value.replaceFirst("(?i)\\s*\\(\\d+\\s+MILIONI\\)\\s*$", "").trim();
    }

    private String normalizeTeamName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String fantaMasterRole(Role role) {
        return switch (role) {
            case PORTIERE -> "P";
            case DIFENSORE -> "D";
            case CENTROCAMPISTA -> "C";
            case ATTACCANTE -> "A";
        };
    }

    private String cellText(Row row, int index) {
        if (row == null || row.getCell(index) == null) return "";
        return new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(row.getCell(index)).trim();
    }

    private double numericCell(Row row, int index) {
        String text = cellText(row, index).replace(',', '.');
        try { return Double.parseDouble(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Costo non valido: " + text); }
    }



    private void copyRosterToHistory(ParticipantEntity participant) {
        long sessionId = System.currentTimeMillis(); // per ora timestamp, poi si può usare session ufficiale
        List<RosterEntity> currentRoster = RosterEntity.list("participant", participant);
        createRoster(sessionId, currentRoster);
    }

    static void createRoster(long sessionId, List<RosterEntity> currentRoster) {
        for (RosterEntity r : currentRoster) {
            RosterHistoryEntity h = new RosterHistoryEntity();
            h.sessionId = sessionId;
            h.participant = r.participant;
            h.player = r.player;
            h.amount = r.amount;
            h.persist();
        }
    }

    @Transactional
    public com.fantasta.dto.ReleaseResultDto svincola(Long participantId, SvincoloRequest req) {
        return svincola(participantId, req, UUID.randomUUID().toString());
    }

    private com.fantasta.dto.ReleaseResultDto svincola(Long participantId, SvincoloRequest req,
                                                        String operationCode) {
        if (participantId == null || req == null || req.playerId == null) {
            throw new BadRequestException("Parametri mancanti per lo svincolo");
        }

        // 🔹 Mercato attivo?
        if (!mercatoService.isMercatoAttivo()) {
            throw new ForbiddenException("Mercato chiuso: svincolo non consentito");
        }
        mercatoService.requireUpdatedQuotes();
        MercatoConfigEntity market = mercatoService.requireConfiguredMarket();

        RosterEntity roster = RosterEntity.find(
                "participant.id = ?1 and player.id = ?2",
                participantId,
                req.playerId
        ).firstResult();

        if (roster == null) {
            throw new NotFoundException("Giocatore non trovato nella rosa");
        }

        ParticipantEntity participant = roster.participant;
        Role role = roster.player.role;
        if (role == null) {
            throw new BadRequestException("Ruolo non valido per lo svincolo");
        }

        boolean goalkeeperPackage = role == Role.PORTIERE;
        List<RosterEntity> entries = goalkeeperPackage
                ? RosterEntity.list("participant = ?1 and player.role = ?2", participant, Role.PORTIERE)
                : List.of(roster);
        boolean departed = entries.stream().allMatch(entry -> !entry.player.active);

        // I giocatori usciti dalla lista non consumano il limite regolamentare.
        int maxSvincoli = mercatoService.getMaxByRole(role.name());
        int fatti = MercatoSvincolo.getCount(participant, role, market.sessionCode);

        if (!departed && fatti >= maxSvincoli) {
            throw new ForbiddenException("Hai già raggiunto il limite massimo di svincoli per ruolo " + role.name());
        }

        double oldAmount = entries.stream().mapToDouble(entry -> entry.amount == null ? 0D : entry.amount).sum();
        double refund = entries.stream().mapToDouble(entry -> entry.player.valore == null ? 0D : entry.player.valore).sum();
        List<String> releasedNames = entries.stream().map(entry -> entry.player.name).toList();

        for (int index = 0; index < entries.size(); index++) {
            RosterEntity entry = entries.get(index);
            PlayerOwnerHistoryEntity.remember(entry.player, participant);
            if (!departed) {
                marketRestrictionService.createRestrictionsAtRelease(
                        entry.player, participant, entry.player.valore == null ? 0D : entry.player.valore,
                        market.sessionCode);
            }
            MarketMovementEntity movement = new MarketMovementEntity();
            movement.participant = participant;
            movement.player = entry.player;
            movement.type = departed ? MarketMovementEntity.Type.DEPARTED : MarketMovementEntity.Type.RELEASE;
            movement.currentValue = entry.player.valore == null ? 0D : entry.player.valore;
            movement.previousRosterAmount = entry.amount == null ? 0D : entry.amount;
            movement.refundedAmount = entry.player.valore == null ? 0D : entry.player.valore;
            movement.playerNameSnapshot = entry.player.name;
            movement.playerTeamSnapshot = entry.player.team;
            movement.sourceParticipantSnapshot = participant.name;
            movement.sessionCode = market.sessionCode;
            movement.operationCode = operationCode;
            movement.countedRelease = !departed && index == 0;
            movement.persist();
            entry.delete();
            entry.player.assigned = false;
        }

        if (!departed) {
            MercatoSvincolo.increment(participant, role, market.sessionCode);
        }

        // La cancellazione rimborsa già il costo rosa: correggiamo il plafond affinché
        // l'incremento effettivo del residuo sia esattamente la quotazione corrente.
        participant.totalCredits += (int) Math.round(refund - oldAmount);
        participant.persist();

        int remaining = participantService.remainingCreditsById(participant.id, participant.totalCredits);

        Log.infof(
                "Svincolato %s (%s) da %s: +%.1f crediti (svincoli %d/%d)",
                String.join(", ", releasedNames), role, participant.name, refund,
                departed ? fatti : fatti + 1, maxSvincoli
        );
        return new com.fantasta.dto.ReleaseResultDto(
                releasedNames, refund, remaining, departed, goalkeeperPackage);
    }

    @Transactional
    public List<RosterDto> getRosters() {
        boolean isAdmin = identity.hasRole("admin");
        List<RosterEntity> entities;

        if (isAdmin) {
            entities = RosterEntity.listAll();
        } else {
            String username = identity.getPrincipal().getName();
            ParticipantEntity p = ParticipantEntity.find("name", username).firstResult();
            if (p == null) {
                throw new IllegalStateException("Partecipante non trovato per utente: " + username);
            }
            entities = RosterEntity.find("participant", p).list();
        }

        return entities.stream()
                .map(r -> new RosterDto(
                        r.participant.id,
                        r.participant.name,
                        r.player.id,
                        r.player.name,
                        r.player.team,
                        r.player.role.name(),
                        r.amount,
                        r.player.valore,
                        r.player.active
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<RosterDto> getRosterByParticipant(Long participantId) {
        List<RosterEntity> rosterEntities = RosterEntity.find("participant.id", participantId).list();
        return toRosterDtoListWithResidui(rosterEntities);
    }

    @Transactional
    public List<ParticipantRosterDto> getAllRostersGrouped() {
        List<RosterEntity> entities = RosterEntity.listAll();

        Map<Long, List<RosterEntity>> grouped = entities.stream()
                .collect(Collectors.groupingBy(r -> r.participant.id));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<RosterEntity> rosterEntities = entry.getValue();
                    List<RosterDto> rosterDtos = toRosterDtoListWithResidui(rosterEntities);
                    RosterEntity sample = rosterEntities.get(0);
                    AppUserEntity account = AppUserEntity.find("participant", sample.participant).firstResult();
                    return new ParticipantRosterDto(
                            sample.participant.id,
                            sample.participant.name,
                            account == null ? null : account.username,
                            rosterDtos
                    );
                })
                .toList();
    }

    @Transactional
    public List<RosterDto> getAllRosters() {
        return RosterEntity.findAll().stream()
                .map(r -> toDto((RosterEntity) r))
                .collect(Collectors.toList());
    }

    private RosterDto toDto(RosterEntity r) {
        return new RosterDto(
                r.participant.id,
                r.participant.name,
                r.player.id,
                r.player.name,
                r.player.team,
                r.player.role.toString(),
                r.amount,
                r.player.valore,
                r.player.active
        );
    }

    private List<RosterDto> toRosterDtoListWithResidui(List<RosterEntity> rosterEntities) {
        if (rosterEntities == null || rosterEntities.isEmpty()) {
            return List.of();
        }

        ParticipantEntity participant = rosterEntities.get(0).participant;
        double totaleDisponibile = participant != null ? participant.totalCredits : 500;
        double spesi = rosterEntities.stream()
                .mapToDouble(r -> r.amount != null ? r.amount : 0)
                .sum();
        double residui = totaleDisponibile - spesi;

        return rosterEntities.stream()
                .map(r -> new RosterDto(
                        r.participant.id,
                        r.participant.name,
                        r.player.id,
                        r.player.name,
                        r.player.team,
                        r.player.role != null ? r.player.role.name() : null,
                        r.amount,
                        r.player.valore,
                        residui, // 👈 calcolato
                        r.player.active
                ))
                .collect(Collectors.toList());
    }



}
