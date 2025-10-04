package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        ElpriserAPI api = new ElpriserAPI();
        Map<String, String> arguments = parseArgs(args);

        // --- Required argument: zone ---
        if (!arguments.containsKey("--zone")) {
            System.err.println("Fel: --zone är obligatoriskt (SE1, SE2, SE3, SE4).");
            System.out.println("Usage: java -jar app.jar --zone SE1|SE2|SE3|SE4 [--date YYYY-MM-DD] [--sorted] [--charging 2h|4h|8h]");
            return;
        }

        String zone = arguments.get("--zone");
        ElpriserAPI.Prisklass priceZone;
        try {
            priceZone = ElpriserAPI.Prisklass.valueOf(zone);
        } catch (IllegalArgumentException e) {
            System.out.println("Ogiltig zon. Giltiga zoner: SE1, SE2, SE3, SE4.");
            return;
        }

        // --- Optional: date ---
        LocalDate date = LocalDate.now();
        if (arguments.containsKey("--date")) {
            try {
                date = LocalDate.parse(arguments.get("--date"), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                System.out.println("Fel datum. Använd formatet YYYY-MM-DD.");
                return;
            }
        }

        // Initialize API
        System.out.println("ElpriserAPI initialiserat. Cachning: På");
        System.out.println("!!! ANVÄNDER MOCK-DATA FÖR TEST !!!");

        List<ElpriserAPI.Elpris> prices = api.getPriser(date, priceZone);
        if (prices == null || prices.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + date + " i " + zone);
            return;
        }

        // Mean price
        double meanPrice = prices.stream().mapToDouble(ElpriserAPI.Elpris::sekPerKWh).average().orElse(0);
        System.out.printf("Medelpris: %.3f SEK/kWh%n", meanPrice);

        // Cheapest & most expensive
        ElpriserAPI.Elpris cheapest = Collections.min(prices, Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));
        ElpriserAPI.Elpris mostExpensive = Collections.max(prices, Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));

        int minStart = cheapest.timeStart().getHour();
        int maxStart = mostExpensive.timeStart().getHour();

        System.out.printf("Lägsta pris: %02d-%02d -> %.1f SEK/kWh%n", minStart, (minStart + 1) % 24, cheapest.sekPerKWh());
        System.out.printf("Högsta pris: %02d-%02d -> %.1f SEK/kWh%n", maxStart, (maxStart + 1) % 24, mostExpensive.sekPerKWh());

        // --- Sorted output ---
        if (arguments.containsKey("--sorted")) {
            List<ElpriserAPI.Elpris> sorted = new ArrayList<>(prices);
            sorted.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));

            for (ElpriserAPI.Elpris p : sorted) {
                int startHour = p.timeStart().getHour();
                int endHour = (startHour + 1) % 24;
                double orePrice = p.sekPerKWh() * 100;
                String formatted = String.format(Locale.forLanguageTag("sv-SE"), "%.2f", orePrice).replace('.', ',');
                System.out.printf("%02d-%02d %s öre%n", startHour, endHour, formatted);
            }
        }

        // --- Charging window (2h, 4h, 8h) ---
        if (arguments.containsKey("--charging")) {
            String hoursArg = arguments.get("--charging").replace("h", "");
            int hours = Integer.parseInt(hoursArg);

            double bestCost = Double.MAX_VALUE;
            ElpriserAPI.Elpris bestStart = null;

            for (int i = 0; i <= prices.size() - hours; i++) {
                List<ElpriserAPI.Elpris> window = prices.subList(i, i + hours);
                double cost = window.stream().mapToDouble(ElpriserAPI.Elpris::sekPerKWh).sum();
                if (cost < bestCost) {
                    bestCost = cost;
                    bestStart = window.get(0);
                }
            }

            if (bestStart != null) {
                double totalCostOre = bestCost * 100;
                String formattedCost = String.format(Locale.forLanguageTag("sv-SE"), "%.1f", totalCostOre).replace('.', ',');
                System.out.printf("Påbörja laddning: bästa %dh-fönster startar %s (total kostnad: %s öre)%n",
                        hours, bestStart.timeStart(), formattedCost);
            }
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(arg, args[i + 1]);
                    i++;
                } else {
                    map.put(arg, "true");
                }
            }
        }
        return map;
    }
}




