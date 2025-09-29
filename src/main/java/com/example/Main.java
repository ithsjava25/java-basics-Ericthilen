// 1. Läs in argument från kommandoraden
// 2. Hämta elpriser från ElpriserAPI
// 3. Beräkna statistik (mean, min, max)
// 4. Hitta laddningsfönster om det efterfrågas
// 5. Skriv ut resultatet i terminalen

package com.example;

import com.example.api.ElpriserAPI;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        // 1. Läs in zon från användaren
        Scanner scanner = new Scanner(System.in);
        System.out.println("Ange zon (SE1-SE4): ");
        String zonStr = scanner.nextLine().toUpperCase();
        ElpriserAPI.Prisklass zon = ElpriserAPI.Prisklass.valueOf(zonStr);

        // 2. Datum (default: idag)
        LocalDate date = LocalDate.now();

        // 3. Hämta priser från ElpriserAPI
        ElpriserAPI api = new ElpriserAPI();
        List<ElpriserAPI.Elpris> priser = api.getPriser(date, zon);

        if (priser.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + date + " i zon " + zon);
            return;
        }

        // 4. Beräkna medelpris
        double sum = 0;
        for (ElpriserAPI.Elpris elpriser : priser) {
            sum += elpriser.sekPerKWh();
        }
        double mean = sum / priser.size();

        // 5. Hitta billigaste och dyraste timme
        ElpriserAPI.Elpris billigast = priser.get(0);
        ElpriserAPI.Elpris dyrast = priser.get(0);

        for (ElpriserAPI.Elpris elpriser : priser) {
            if (elpriser.sekPerKWh() < billigast.sekPerKWh()) {
                billigast = elpriser;
            }

            if (elpriser.sekPerKWh() > dyrast.sekPerKWh()) {
                dyrast = elpriser;
            }
        }

        // 6. Skriv ut resultat
        System.out.println("\n Elpriser för " + date + " i zon " + zon);
        System.out.printf("Medelpris: %.2f öre/kW%n", mean * 100);
        System.out.printf("Billigaste timme: %s (%.2f öre/kWh)%n", billigast.timeStart().toLocalTime(), billigast.sekPerKWh() * 100);
        System.out.printf("Dyraste timme: %s (%.2f öre/kWh)%n", dyrast.timeStart().toLocalTime(), dyrast.sekPerKWh() * 100);

        // 7. Enkel laddningsfönster för 2 timmar
        System.out.println("\n Optimal 2-timmars laddning:");
        double minSum2h = Double.MAX_VALUE;
        int startIndex = 0;

        for (int i = 0; i < priser.size() - 1; i++) {
            double sum2h = priser.get(i).sekPerKWh() + priser.get(i + 1).sekPerKWh();
            if (sum2h < minSum2h) {
                minSum2h = sum2h;
                startIndex = i;
            }
        }

        System.out.printf("%s - %s, Total kostnad: %.2f öre%n",
                priser.get(startIndex).timeStart().toLocalTime(),
                priser.get(startIndex + 1).timeEnd().toLocalTime(),
                minSum2h * 100
        );

    }
}
