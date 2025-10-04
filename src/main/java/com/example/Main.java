package com.example;

// Importerar API-klasser för elpriser
import com.example.api.ElpriserAPI;
import com.example.api.ElpriserAPI.Elpris;
import com.example.api.ElpriserAPI.Prisklass;

// Importerar datum & tidshantering
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        // Om inga argument anges, visa hjälptext och avsluta
        if (args.length == 0) {
            printHelp();
            return;
        }

        // Skapar en instans av API-klassen för att hämta elpriser
        ElpriserAPI api = new ElpriserAPI();

        // Initierar variabler för användarens val
        Prisklass zone = null; // Elområde (SE1-SE4)
        LocalDate selectedDate = LocalDate.now(); // Standarddatum: idag
        boolean sortDescending = false;
        int chargingDuration = 0; // Antal timmar för laddning (0 = ingen laddning)

        // Loopar igenom argumenten och tolkar dem
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    // Nästa argument ska vara zonnamnet
                    if (i + 1 < args.length) {
                        String z = args[++i].toUpperCase();
                        zone = switch (z) {
                            case "SE1" -> zone = Prisklass.SE1;
                            case "SE2" -> zone = Prisklass.SE2;
                            case "SE3" -> zone = Prisklass.SE3;
                            case "SE4" -> zone = Prisklass.SE4;
                            default -> zone; // Ogiltig zon
                        };
                    }
                    break;
                case "--date":
                        if (i + 1 < args.length) {
                            try {
                                selectedDate = LocalDate.parse(args[++i]);
                            } catch (Exception e) {
                                System.out.println("Ogiltigt datumformat. Använd YYY-MM-DD.");
                                return;
                            }
                        }
                        break;
                case "--sorted":
                        // Flagga för att sortera priser fallande
                            sortDescending = true;
                            break;
                case "--charging":
                    // Nästa argument ska vara laddningstid
                    if (i + 1 < args.length) {
                        String val  = args[++i].replace("h", "");
                        try {
                            chargingDuration = Integer.parseInt(val);
                        } catch (Exception e) {
                            System.out.println("Ogiltigt värde för --charging--");
                            return;
                        }
                    }
                    break;
                case "--help":
                    // Visa hjälptext och avsluta
                    printHelp();
                    return;
            }
        }

        // Om zon inte angivits, visa hjälptext och avsluta
        if (zone == null) {
            System.out.println("Ogiltig zon: --zone måste följas av SE1, SE2, SE3 eller SE4");
            printHelp();
            return;
        }

        // Hämta priser för både idag och imorgon
        List<Elpris> prices = new ArrayList<>();
        prices.addAll(api.getPriser(selectedDate, zone));
        prices.addAll(api.getPriser(selectedDate.plusDays(1), zone));

        // Om klockan är efter 13 idag, filtrera bort timmar som redan passerat
        LocalDateTime now = LocalDateTime.now();
        if (selectedDate.equals(LocalDate.now()) && now.toLocalTime().isAfter(LocalTime.of(13, 0))) {
            List<Elpris> filteredPrices = new  ArrayList<>();
            for (Elpris p : prices) {
                if (!p.timeStart().toLocalDateTime().isBefore(now)) {
                    filteredPrices.add(p);
                }
            }
            prices =  filteredPrices;
        }

        // Om inga priser hittades, avluta
        if (prices.isEmpty()) {
            System.out.println("Inga priser hittades för " + zone + " " + selectedDate);
            return;
        }

        // Sortera priser fallande om flaggan är satt
        if (sortDescending) {
            prices.sort((p1, p2) -> {
                int cmp = Double.compare(p2.sekPerKWh(), p1.sekPerKWh());
                if (cmp != 0) return cmp;
                return p1.timeStart().compareTo(p2.timeStart());
            });
        }

        // Skriv ut alla priser i format HH-HH XX,XX öre
        System.out.println("Elpriser för " + zone + " " + selectedDate + " (" + prices.size() + "timmar):");
        for (Elpris p : prices) {
            int startHour = p.timeStart().getHour();
            int endHour = p.timeEnd().getHour();
            double ore = p.sekPerKWh() * 100;
            String oreFormatted = String.format("%.2f", ore).replace('.', ',');
            System.out.println(String.format("%02d-%02d %s öre", startHour, endHour % 24, oreFormatted));
        }

        // Statistik: medelpris per timme
        double[] sumPerHour = new double[24];
        int[] countPerHour = new int [24];

        for (Elpris p : prices) {
            int hour = p.timeStart().getHour();
            sumPerHour[hour] += p.sekPerKWh();
            countPerHour[hour]++;
        }

        // Hitta billigast och dyraste timme baserat på medelpris
        double minAvg = Double.MAX_VALUE;
        double maxAvg = Double.MIN_VALUE;
        int minHour = 0;
        int maxHour = 0;

        for (int h = 0; h < 24; h++) {
            if (countPerHour[h] > 0) {
                double avg = sumPerHour[h] / countPerHour[h];
                if (avg < minAvg) {
                    minAvg = avg;
                    minHour = h;
                }
                if (avg < minAvg) {
                    minAvg = avg;
                    minHour = h;
                }
                if (avg > maxAvg) {
                    maxAvg = avg;
                    maxHour = h;
                }
            }
        }

        // Beräkna total medelpris för alla timmar
        Double totalSum = prices.stream().mapToDouble(Elpris::sekPerKWh).sum();
        double avgTotal = totalSum / prices.size();

        // Skriv ut statistik
        System.out.println("Lägsta pris: " + String.format("%.2f", minAvg * 100).replace('.', ',') + " öre (" +
                String.format("%02d", minHour) + "-" + String.format("%02d", (minHour + 1) % 24) + ")");
        System.out.println("Högsta pris: " + String.format("%.2f", maxAvg * 100).replace('.', ',') + " öre (" +
                String.format("%02d", maxHour) + "-" + String.format("%02d", (maxHour + 1) % 24) + ")");
        System.out.println("Medelpris: " + String.format("%.2f", avgTotal * 100).replace('.', ',') + " öre");

        // Sliding window (hitta billigaste laddningsfönstret)
        if (chargingDuration > 0 && chargingDuration <= prices.size()) {
            double bestSum = Double.MAX_VALUE;
            int bestStartIdx = 0;

            for (int i = 0; i  <= prices.size() - chargingDuration; i++) {
                double windowSum = 0;
                for (int j = 0; j < chargingDuration; j++) {
                    windowSum += prices.get(i + j).sekPerKWh();
                }
                if (windowSum < bestSum) {
                    bestSum = windowSum;
                    bestStartIdx = i;
                }
            }

            // Skriv ut laddningstimmar
            System.out.println("\nPåbörja laddning under de " + chargingDuration + " billigaste timmarna:");
            for (int i = 0; i < chargingDuration; i++) {
                Elpris p = prices.get(bestStartIdx + i);
                int hour = p.timeStart().getHour();
                int minute =  p.timeStart().getMinute();
                double ore =  p.sekPerKWh() * 100;
                String oreFormatted = String.format("%.2f", ore).replace('.', '.');
                System.out.println("kl " + String.format("%02d:%02d", hour, minute) + " " + oreFormatted + " öre");
            }

            // Skriv ut medelpris för laddningsfönstret
            double avgWindowPrice = bestSum / chargingDuration;
            System.out.println("Medelpris för fönster: " + String.format("%.2f", avgWindowPrice * 100).replace('.', ',') + " öre");
        }
    }

    // Hjälptext som visas med --help eller vid felaktig inmatning
    private static void printHelp() {
        System.out.println("Usage: java Main --zone <SE1|SE2|SE3|SE4> [alternativ]");
        System.out.println("Alternativ:");
        System.out.println("  --zone      Elområde (obligatoriskt)");
        System.out.println("  --date      Datum i format YYYY-MM-DD (default: idag)");
        System.out.println("  --sorted    Sortera priser fallande (dyrast först)");
        System.out.println("  --charging  Laddningstid: 2h, 4h eller 8h");
    }
}