package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class Main {

    public record PriceEntry(
            ZonedDateTime zonedDateTime,
            double price,
            String sourceDay
    ) {
        
    }


    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();

        LocalDate today = LocalDate.now();
        ElpriserAPI.Prisklass zone = ElpriserAPI.Prisklass.SE3;

        ElpriserAPI api = null;
        List<ElpriserAPI.Elpris> todayPrices = api.getPriser(today, zone);


        List<ElpriserAPI.Elpris> tomorrowPrices = api.getPriser(today.plusDays(1), zone);

        List<PriceEntry> entries = todayPrices.stream()
                .map(e -> new PriceEntry(
                        e.timeStart().withZoneSameInstant(ZoneId.of("Europe/Stockholm")),
                        e.sekPerKWh(),
                        "today"
                ))
                .toList();



    }
}
