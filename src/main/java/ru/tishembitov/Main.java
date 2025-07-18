package ru.tishembitov;

import ru.tishembitov.api.ApiClient;
import ru.tishembitov.bot.BotLogic;

public class Main {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("DATS_TOKEN");
        String url = "https://games-test.datsteam.dev";
        ApiClient api = new ApiClient(url, token);
        BotLogic bot = new BotLogic(api);
        bot.play();
    }
} 