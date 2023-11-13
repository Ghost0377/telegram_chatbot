package chatBot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Map;

public class Main {
    public static void main(String[] args) throws TelegramApiException {
        Map<String, String> environmentVariables = System.getenv();

        String tokenEnvironmentVariable = environmentVariables.get("TOKEN");
        String botNameEnvironmentVariable = environmentVariables.get("BOTNAME");

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new Bot(tokenEnvironmentVariable,botNameEnvironmentVariable));
    }
}