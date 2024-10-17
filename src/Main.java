import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    private static final String jdbcUrl = "jdbc:mysql://localhost:3306/project_java_challenge";
    private static final String username = "root";
    private static final String password = "581120eRHM$$";

    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

        String apiKey = "59c6dad88ec2adb26f0c790f";
        String baseCurrency = "USD";
        String url = String.format("https://v6.exchangerate-api.com/v6/%s/latest/%s", apiKey, baseCurrency);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(Main::parseJSON)
                .join();

        startCurrencyConversion();
    }

    private static String parseJSON(String responseBody) {
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String baseCode = jsonObject.get("base_code").getAsString();
        System.out.println("Moneda base: " + baseCode);
        JsonObject conversionRates = jsonObject.getAsJsonObject("conversion_rates");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            String[] currencies = {"USD", "ARS", "BOB", "BRL", "CLP", "COP"};

            for (String currency : currencies) {
                String deleteSQL = "DELETE FROM conversion_rates WHERE currency = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSQL)) {
                    deleteStatement.setString(1, currency);
                    int rowsAffected = deleteStatement.executeUpdate();
                    System.out.println("Moneda eliminada: " + currency + " - Filas afectadas: " + rowsAffected);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            String insertSQL = "INSERT INTO conversion_rates (currency, rate) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
                conversionRates.entrySet().forEach(entry -> {
                    String currency = entry.getKey();
                    Double rate = entry.getValue().getAsDouble();

                    if (Arrays.asList(currencies).contains(currency)) {
                        System.out.println("Moneda: " + currency + " - Tasa: " + rate);
                        try {
                            preparedStatement.setString(1, currency);
                            preparedStatement.setDouble(2, rate);
                            preparedStatement.executeUpdate();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return baseCode;
    }


    private static void startCurrencyConversion() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            displayMenu();
            int option = getUserOption(scanner);
            running = handleUserOption(option, scanner);
        }
        scanner.close();
    }

    private static void displayMenu() {
        System.out.println("=== Conversor de Monedas ===");
        System.out.println("1. Convertir de USD a otra moneda");
        System.out.println("2. Convertir de otra moneda a USD");
        System.out.println("3. Salir");
        System.out.print("Selecciona una opción: ");
    }

    private static int getUserOption(Scanner scanner) {
        return scanner.nextInt();
    }

    private static boolean handleUserOption(int option, Scanner scanner) {
        switch (option) {
            case 1:
                convertFromUSD(scanner);
                return true;
            case 2:
                convertToUSD(scanner);
                return true;
            case 3:
                System.out.println("¡Gracias por usar el conversor de monedas!");
                return false;
            default:
                System.out.println("Opción inválida. Intenta nuevamente.");
                return true;
        }
    }

    private static void convertFromUSD(Scanner scanner) {
        System.out.print("Ingresa la cantidad en USD: ");
        double amount = scanner.nextDouble();
        System.out.print("Ingresa la moneda de destino (ejemplo: ARS, BRL, COP): ");
        String targetCurrency = scanner.next();

        double rate = getConversionRate("USD", targetCurrency);

        if (rate > 0) {
            double convertedAmount = amount * rate;
            System.out.printf("%.2f USD son %.2f %s%n", amount, convertedAmount, targetCurrency);
        } else {
            System.out.println("No se encontró la tasa de conversión para " + targetCurrency);
        }
    }


    private static void convertToUSD(Scanner scanner) {
        System.out.print("Ingresa la cantidad en moneda: ");
        double amount = scanner.nextDouble();
        System.out.print("Ingresa la moneda de origen (ejemplo: ARS, BRL, COP): ");
        String sourceCurrency = scanner.next();

        double rate = getConversionRate(sourceCurrency, "USD");

        if (rate > 0) {
            double convertedAmount = amount / rate;
            System.out.printf("%.2f %s son %.2f USD%n", amount, sourceCurrency, convertedAmount);
        } else {
            System.out.println("No se encontró la tasa de conversión para " + sourceCurrency);
        }
    }

    private static double getConversionRate(String fromCurrency, String toCurrency) {
        double rate = 0;
        String query;

        if (fromCurrency.equals("USD")) {
            query = "SELECT rate FROM conversion_rates WHERE currency = ?";
        } else {
            query = "SELECT rate FROM conversion_rates WHERE currency = ?";
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            if (fromCurrency.equals("USD")) {
                preparedStatement.setString(1, toCurrency);
            } else {
                preparedStatement.setString(1, fromCurrency);
            }

            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                rate = resultSet.getDouble("rate");

                if (!fromCurrency.equals("USD")) {
                    rate = 1 / rate; // Invertir la tasa para convertir de otra moneda a USD
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rate;
    }

}
