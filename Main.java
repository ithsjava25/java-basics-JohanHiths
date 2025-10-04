package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class Main {



    // Your standard model for working with prices
    public record PriceEntry(
            java.time.ZonedDateTime zonedDateTime,
            double price,
            String sourceDay
    ) {}

    public static void main(String[] args) {
        // 1. Parse CLI arguments (very simple for now)
        String zoneArg = null;
        String dateArg = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone" -> zoneArg = args[++i];
                case "--date" -> dateArg = args[++i];
                case "--help" -> {
                    printHelp();
                    return;
                }
            }
        }

        if (zoneArg == null) {
            System.err.println("Error: --zone is required");
            printHelp();
            return;
        }

        ElpriserAPI.Prisklass zone = ElpriserAPI.Prisklass.valueOf(zoneArg);
        LocalDate date = (dateArg != null) ? LocalDate.parse(dateArg) : LocalDate.now();

        // 2. Fetch data
        ElpriserAPI api = new ElpriserAPI();
        List<PriceEntry> entries = new ArrayList<>();

        entries.addAll(fetchFor(api, date, zone, "today"));
        entries.addAll(fetchFor(api, date.plusDays(1), zone, "tomorrow"));

        // 3. Print results
        if (entries.isEmpty()) {
            System.out.println("No data available for " + zone + " on " + date);
        } else {
            System.out.println("Electricity prices for zone " + zone + ":");
            for (PriceEntry e : entries) {
                System.out.printf("%s | %.3f SEK/kWh | %s%n",
                        e.zonedDateTime().toLocalDateTime(),
                        e.price(),
                        e.sourceDay());
            }
        }
    }

    // --- Helper functions ---

    private static List<PriceEntry> fetchFor(ElpriserAPI api,
                                             LocalDate date,
                                             ElpriserAPI.Prisklass zone,
                                             String sourceDay) {
        List<ElpriserAPI.Elpris> raw = api.getPriser(date, zone);
        return raw.stream()
                .map(e -> new PriceEntry(
                        e.timeStart().withZoneSameInstant(ZoneId.of("Europe/Stockholm")),
                        e.sekPerKWh(),
                        sourceDay))
                .toList();
    }

    private static void printHelp() {
        System.out.println("""
            Usage:
              java -cp target/classes com.example.Main --zone SE3 [--date YYYY-MM-DD]

            Options:
              --zone SE1|SE2|SE3|SE4   (required)
              --date YYYY-MM-DD        (optional, defaults to today)
              --help                   Show this help
              --charging
              --sorting
              --sorted
            """);




    }
}

