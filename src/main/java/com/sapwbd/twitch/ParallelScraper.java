package com.sapwbd.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParallelScraper {

    private static ObjectMapper mapper;
    private static ObjectReader reader;
    private static ObjectWriter writer;

    private static ExecutorService execService;
    private static String clientId;
    private static String clientSecret;
    private static String appToken;
    private static String broadcasterName;
    private static String broadcasterId;
    private static String outputDirectoryPath;

    private static CloseableHttpClient httpClient;

    // Network bound as well, can get away with using more threads (the factor here is just a guess)
    public static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    public static void main(String[] args) throws IOException, URISyntaxException {
        long startTime = System.currentTimeMillis();

        try {
            System.out.printf("Start time: %d%n", startTime);

            if (args.length < 4) {
                System.out.println("Invalid args");
                throw new RuntimeException("Missing arguments. Pass the mandatory client_id, client_secret, broadcaster_name and output_directory arguments");
            }

            // args[0] clientId
            // args[1] clientSecret
            // args[2] broadcasterName
            // args[3] destLocation
            clientId = args[0];
            clientSecret = args[1];
            broadcasterName = args[2];
            outputDirectoryPath = args[3];

            // delegate
            scrapeEmotes();

        } finally {
            cleanup();
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("End time: %d%n", endTime);
        System.out.printf("Total time: %ds", (endTime - startTime) / 1000);
    }

    private static void cleanup() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                // Already closed? Ignore
            }
        }
        if (execService != null) {
            execService.shutdown();
            try {
                if (!execService.awaitTermination(5, TimeUnit.MINUTES)) {
                    execService.shutdownNow();
                }
            } catch (InterruptedException e) {
                // Received thread interrupt, ignore since we exit soon after anyway
            }
        }
    }

    private static void scrapeEmotes() throws URISyntaxException, IOException {
        scrapeTwEmotes();
        scrapeBttvEmotes();
        scrapeFfzEmotes();
        scrape7tvEmotes();
    }

    private static void scrape7tvEmotes() throws URISyntaxException, IOException {
        // 7tv emotes

        // channel
        // https://api.7tv.app/v2/users/144398644/emotes
        HttpGet _7tvChannelEmotesGet = new HttpGet(new URIBuilder("https://api.7tv.app/v2/users").appendPath(getBroadcasterId())
                                                                                                 .appendPath("emotes")
                                                                                                 .build());
        try (CloseableHttpResponse _7tvChannelEmotesGetR = httpClient.execute(_7tvChannelEmotesGet)) {
            JsonNode responseNode = getReader().readTree(_7tvChannelEmotesGetR.getEntity().getContent());
            EntityUtils.consume(_7tvChannelEmotesGetR.getEntity());
            parse7tvEmotesAndScheduleDlTasks(responseNode, outputDirectoryPath);
        }

        // global
        // https://api.7tv.app/v2/emotes/global
        HttpGet _7tvGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.7tv.app/v2/emotes/global").build());
        try (CloseableHttpResponse _7tvGlobalEmotesGetR = httpClient.execute(_7tvGlobalEmotesGet)) {
            JsonNode responseNode = getReader().readTree(_7tvGlobalEmotesGetR.getEntity().getContent());
            EntityUtils.consume(_7tvGlobalEmotesGetR.getEntity());
            parse7tvEmotesAndScheduleDlTasks(responseNode, Paths.get(outputDirectoryPath, "globals").toString());
        }
    }

    private static void parse7tvEmotesAndScheduleDlTasks(JsonNode emoteNodes, String outputDirectoryPath) {
        for (JsonNode emoteNode : emoteNodes) {
            String emoteUrl = emoteNode.get("urls").get(emoteNode.get("urls").size() - 1).get(1).asText();
            String emoteCode = emoteNode.get("name").asText();
            String mimeString = emoteNode.get("mime").asText();
            String[] mimeTokens = mimeString.split("/");
            String emoteType = mimeTokens[mimeTokens.length - 1];

            getExecService().submit(new EmoteGet(emoteUrl, emoteCode, emoteType, outputDirectoryPath));
        }
    }

    private static void scrapeFfzEmotes() throws URISyntaxException, IOException {
        // ffz emotes

        // channel
        // https://api.betterttv.net/3/cached/frankerfacez/users/twitch/144398644
        HttpGet ffzChannelEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/frankerfacez/users/twitch").appendPath(getBroadcasterId())
                                                                                                                                .build());
        try (CloseableHttpResponse ffzChannelEmotesGetR = httpClient.execute(ffzChannelEmotesGet)) {
            JsonNode responseNode = getReader().readTree(ffzChannelEmotesGetR.getEntity().getContent());
            EntityUtils.consume(ffzChannelEmotesGetR.getEntity());
            parseFfzEmotesAndScheduleDlTasks(responseNode, outputDirectoryPath);
        }

        // global
        // https://api.betterttv.net/3/cached/frankerfacez/emotes/global
        HttpGet ffzGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/frankerfacez/emotes/global").build());
        try (CloseableHttpResponse ffzGlobalEmotesGetR = httpClient.execute(ffzGlobalEmotesGet)) {
            JsonNode responseNode = getReader().readTree(ffzGlobalEmotesGetR.getEntity().getContent());
            EntityUtils.consume(ffzGlobalEmotesGetR.getEntity());
            parseFfzEmotesAndScheduleDlTasks(responseNode, Paths.get(outputDirectoryPath, "globals").toString());
        }
    }

    private static void parseFfzEmotesAndScheduleDlTasks(JsonNode emoteNodes, String outputDirectoryPath) {
        for (JsonNode emoteNode : emoteNodes) {
            String emoteUrl = null;
            for (int imDimension : new int[]{4, 2, 1}) {
                if (!emoteNode.get("images").path(String.format("%dx", imDimension)).isNull()) {
                    emoteUrl = emoteNode.get("images").get(String.format("%dx", imDimension)).asText();
                    break;
                }
            }
            String emoteCode = emoteNode.get("code").asText();
            String emoteType = emoteNode.get("imageType").asText();

            if (emoteUrl != null) {
                getExecService().submit(new EmoteGet(emoteUrl, emoteCode, emoteType, outputDirectoryPath));
            }
        }
    }

    private static void scrapeBttvEmotes() throws URISyntaxException, IOException {
        // bttv emotes

        // channel & shared
        // https://api.betterttv.net/3/cached/users/twitch/144398644
        HttpGet bttvChannelEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/users/twitch").appendPath(getBroadcasterId())
                                                                                                                    .build());
        try (CloseableHttpResponse bttvChannelEmotesGetR = httpClient.execute(bttvChannelEmotesGet)) {
            JsonNode responseNode = getReader().readTree(bttvChannelEmotesGetR.getEntity().getContent());
            // channel
            JsonNode channelEmotes = responseNode.get("channelEmotes");
            parseBttvEmotesAndScheduleDlTasks(channelEmotes, outputDirectoryPath);
            // shared
            JsonNode sharedEmotes = responseNode.get("sharedEmotes");
            EntityUtils.consume(bttvChannelEmotesGetR.getEntity());
            parseBttvEmotesAndScheduleDlTasks(sharedEmotes, outputDirectoryPath);
        }

        // global
        // https://api.betterttv.net/3/cached/emotes/global
        HttpGet bttvGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/emotes/global").build());
        try (CloseableHttpResponse bttvGlobalEmotesGetR = httpClient.execute(bttvGlobalEmotesGet)) {
            JsonNode responseNode = getReader().readTree(bttvGlobalEmotesGetR.getEntity().getContent());
            EntityUtils.consume(bttvGlobalEmotesGetR.getEntity());
            parseBttvEmotesAndScheduleDlTasks(responseNode, Paths.get(outputDirectoryPath, "globals").toString());
        }
    }

    private static void parseBttvEmotesAndScheduleDlTasks(JsonNode emoteNodes, String outputDirectoryPath) {
        for (JsonNode emoteNode : emoteNodes) {
            String emoteId = emoteNode.get("id").asText();
            String emoteCode = emoteNode.get("code").asText();
            String emoteType = emoteNode.get("imageType").asText();

            // https://cdn.betterttv.net/emote/5e9e23e174046462f767475b/3x
            getExecService().submit(new EmoteGet(String.format("%s/%s/%s", "https://cdn.betterttv.net/emote", emoteId, "3x"), emoteCode, emoteType, outputDirectoryPath));
        }
    }

    private static void scrapeTwEmotes() throws URISyntaxException, IOException {
        // twitch emotes

        // common headers
        BasicHeader authHeader = new BasicHeader("Authorization", String.format("Bearer %s", getAppToken()));
        BasicHeader clientIdHeader = new BasicHeader("Client-Id", clientId);

        // channel
        // https://api.twitch.tv/helix/chat/emotes
        HttpGet twChannelEmotesGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/chat/emotes").addParameter("broadcaster_id", getBroadcasterId())
                                                                                                          .build());
        twChannelEmotesGet.setHeaders(authHeader, clientIdHeader);
        try (CloseableHttpResponse twChannelEmotesGetR = httpClient.execute(twChannelEmotesGet)) {
            JsonNode responseNode = getReader().readTree(twChannelEmotesGetR.getEntity().getContent());
            EntityUtils.consume(twChannelEmotesGetR.getEntity());
            parseTwEmotesAndScheduleDlTasks(responseNode, outputDirectoryPath);
        }

        // global
        // https://api.twitch.tv/helix/chat/emotes/global
        HttpGet twGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/chat/emotes/global").build());
        twGlobalEmotesGet.setHeaders(authHeader, clientIdHeader);
        try (CloseableHttpResponse twGlobalEmotesGetR = httpClient.execute(twGlobalEmotesGet)) {
            JsonNode responseNode = getReader().readTree(twGlobalEmotesGetR.getEntity().getContent());
            EntityUtils.consume(twGlobalEmotesGetR.getEntity());
            parseTwEmotesAndScheduleDlTasks(responseNode, Paths.get(outputDirectoryPath, "globals").toString());
        }
    }

    private static void parseTwEmotesAndScheduleDlTasks(JsonNode emoteNodes, String outputDirectoryPath) {
        for (JsonNode emoteNode : emoteNodes.get("data")) {
            String emoteUrl = emoteNode.get("images").get("url_4x").asText();
            String emoteCode = emoteNode.get("name").asText();

            getExecService().submit(new EmoteGet(emoteUrl, emoteCode, null, outputDirectoryPath));
        }
    }

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper;
    }

    private static ObjectReader getReader() {
        if (reader == null) {
            reader = getMapper().reader();
        }
        return reader;
    }

    private static ObjectWriter getWriter() {
        if (writer == null) {
            writer = getMapper().writer();
        }
        return writer;
    }

    private static ExecutorService getExecService() {
        if (execService == null) {
            execService = initializeExecutorService();
        }
        return execService;
    }

    private static ExecutorService initializeExecutorService() {
        execService = Executors.newFixedThreadPool(THREAD_COUNT);
        return execService;
    }

    private static CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
        }
        return httpClient;
    }

    private static String getBroadcasterId() throws URISyntaxException, IOException {
        if (broadcasterId == null) {
            broadcasterId = getBroadcasterId(broadcasterName, clientId, getAppToken());
        }
        return broadcasterId;
    }

    private static String getAppToken() throws IOException {
        if (appToken == null) {
            appToken = getAppToken(clientId, clientSecret);
        }
        return appToken;
    }

    private static String getAppToken(String clientId, String clientSecret) throws IOException {
        // OAuth2 token
        // https://id.twitch.tv/oauth2/token
        String oAuth2Url = "https://id.twitch.tv/oauth2/token";
        HttpPost tokenGet = new HttpPost(oAuth2Url);
        List<NameValuePair> encodedParams = new ArrayList<>();
        encodedParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
        encodedParams.add(new BasicNameValuePair("client_id", clientId));
        encodedParams.add(new BasicNameValuePair("client_secret", clientSecret));
        tokenGet.setEntity(new UrlEncodedFormEntity(encodedParams));

        try (CloseableHttpResponse tokenGetR = getHttpClient().execute(tokenGet)) {
            JsonNode responseNode = getReader().readTree(tokenGetR.getEntity().getContent());
            EntityUtils.consume(tokenGetR.getEntity());
            return responseNode.get("access_token").asText();
        }
    }

    private static String getBroadcasterId(String broadcasterName, String clientId, String bearerToken) throws URISyntaxException, IOException {
        // Users
        // Map broadcaster_name -> broadcaster_id
        // https://api.twitch.tv/helix/users
        HttpGet usersGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/users").addParameter("login", broadcasterName)
                                                                                          .build());
        usersGet.addHeader("Client-Id", clientId);
        usersGet.addHeader("Authorization", String.format("Bearer %s", bearerToken));
        try (CloseableHttpResponse usersGetR = httpClient.execute(usersGet)) {
            JsonNode responseNode = getReader().readTree(usersGetR.getEntity().getContent());
            EntityUtils.consume(usersGetR.getEntity());
            return responseNode.get("data").get(0).get("id").asText();
        }
    }
}
