package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {

    public record PriceEntry(
            ZonedDateTime zonedDateTime,
            double price,
            String sourceDay
    ) {}

    public static void main(String[] args) {
        String zoneArg = null;
        String dateArg = null;
        String chargingArg = null;
        boolean sorted = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone" -> zoneArg = args[++i];
                case "--date" -> dateArg = args[++i];
                case "--charging" -> chargingArg = args[++i];
                case "--sorted" -> sorted = true;
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

        // Fetch today + tomorrow
        ElpriserAPI api = new ElpriserAPI();
        List<PriceEntry> entries = new ArrayList<>();
        entries.addAll(fetchFor(api, date, zone, "today"));
        entries.addAll(fetchFor(api, date.plusDays(1), zone, "tomorrow"));

        if (entries.isEmpty()) {
            System.out.println("No data available for " + zone + " on " + date);
            return;
        }

        // --- NEW: ensure chronological order for calculations ---
        entries.sort(Comparator.comparing(PriceEntry::zonedDateTime));

        // --- Statistics ---
        printStatistics(entries);

        // --- Print prices (in requested order) ---
        if (sorted) {
            entries.sort(Comparator.comparingDouble(PriceEntry::price).reversed());
            System.out.println("\nElectricity prices for zone " + zone + " (sorted by highest price):");
        } else {
            System.out.println("\nElectricity prices for zone " + zone + " (chronological):");
        }

        for (PriceEntry e : entries) {
            System.out.printf("%s | %.3f SEK/kWh | %s%n",
                    e.zonedDateTime().toLocalDateTime(),
                    e.price(),
                    e.sourceDay());
        }

        // Handle charging option
        if (chargingArg != null) {
            int hours = parseChargingHours(chargingArg);
            if (hours > 0) {
                System.out.println();
                findOptimalChargingWindow(entries, hours);
            } else {
                System.err.println("Invalid --charging value: " + chargingArg);
            }
        }
    }

    // --- NEW: Mean, cheapest, most expensive ---
    private static void printStatistics(List<PriceEntry> entries) {
        double sum = entries.stream().mapToDouble(PriceEntry::price).sum();
        double mean = sum / entries.size();

        PriceEntry min = entries.stream()
                .min(Comparator.comparingDouble(PriceEntry::price)
                        .thenComparing(PriceEntry::zonedDateTime))
                .orElse(null);

        PriceEntry max = entries.stream()
                .max(Comparator.comparingDouble(PriceEntry::price)
                        .thenComparing(PriceEntry::zonedDateTime))
                .orElse(null);

        System.out.printf("Mean price: %.3f SEK/kWh%n", mean);
        if (min != null) {
            System.out.printf("Cheapest hour: %s | %.3f SEK/kWh (%s)%n",
                    min.zonedDateTime().toLocalDateTime(),
                    min.price(),
                    min.sourceDay());
        }
        if (max != null) {
            System.out.printf("Most expensive hour: %s | %.3f SEK/kWh (%s)%n",
                    max.zonedDateTime().toLocalDateTime(),
                    max.price(),
                    max.sourceDay());
        }
    }

    // --- Helpers (same as before) ---
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

    private static int parseChargingHours(String arg) {
        return switch (arg) {
            case "2h" -> 2;
            case "4h" -> 4;
            case "8h" -> 8;
            default -> -1;
        };
    }

    private static void findOptimalChargingWindow(List<PriceEntry> entries, int hours) {
        if (entries.size() < hours) {
            System.out.println("Not enough data for " + hours + "h charging window.");
            return;
        }

        double bestSum = Double.MAX_VALUE;
        int bestIndex = -1;

        for (int i = 0; i <= entries.size() - hours; i++) {
            double sum = 0;
            for (int j = i; j < i + hours; j++) {
                sum += entries.get(j).price();
            }
            if (sum < bestSum) {
                bestSum = sum;
                bestIndex = i;
            }
        }

        if (bestIndex >= 0) {
            System.out.println("Optimal " + hours + "h charging window:");
            for (int k = bestIndex; k < bestIndex + hours; k++) {
                PriceEntry e = entries.get(k);
                System.out.printf("  %s | %.3f SEK/kWh%n",
                        e.zonedDateTime().toLocalDateTime(),
                        e.price());
            }
            System.out.printf("Total cost: %.3f SEK (avg %.3f SEK/h)%n",
                    bestSum, bestSum / hours);
        }
    }

    private static void printHelp() {
        System.out.println("""
            Usage:
              java -cp target/classes com.example.Main --zone SE3 [--date YYYY-MM-DD] [--sorted] [--charging 2h|4h|8h]

            Options:
              --zone SE1|SE2|SE3|SE4   (required)
              --date YYYY-MM-DD        (optional, defaults to today)
              --sorted                 (optional, show prices from highest to lowest)
              --charging 2h|4h|8h      (optional, find cheapest charging window)
              --help                   Show this help
            """);
    }
}




