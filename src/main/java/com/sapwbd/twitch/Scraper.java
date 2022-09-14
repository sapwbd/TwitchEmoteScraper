package com.sapwbd.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class Scraper {

    public static final Pattern INVALID_FILENAME_CHAR_PATTERN = Pattern.compile("[^\\w.-]");

    private static ObjectMapper mapper;
    private static ObjectReader reader;
    private static ObjectWriter writer;
    private static ExecutorService execService;

    private static CloseableHttpClient httpClient;

    public static void main(String[] args) throws IOException, URISyntaxException {
        long startTime = System.currentTimeMillis();
        System.out.printf("Start time: %d%n", startTime);
        // args[0] clientId
        // args[1] clientSecret
        // args[2] broadcasterName
        // args[3] destLocation
        if (args.length < 4) {
            System.out.println("Pass valid client_id, client_secret, broadcaster_name and output_directory as arguments");
            return;
        }

        String clientId = args[0];
        String clientSecret = args[1];
        String broadcasterName = args[2];
        String outputDirectoryPath = args[3];

        // Initialize httpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // OAuth2 token
            // https://id.twitch.tv/oauth2/token
            String oAuth2Url = "https://id.twitch.tv/oauth2/token";
            HttpPost tokenGet = new HttpPost(oAuth2Url);
            List<NameValuePair> encodedParams = new ArrayList<>();
            encodedParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
            encodedParams.add(new BasicNameValuePair("client_id", clientId));
            encodedParams.add(new BasicNameValuePair("client_secret", clientSecret));
            tokenGet.setEntity(new UrlEncodedFormEntity(encodedParams));

            CloseableHttpResponse tokenGetR = httpClient.execute(tokenGet);
            JsonNode responseNode = getReader().readTree(tokenGetR.getEntity().getContent());
            String bearerToken = responseNode.get("access_token").asText();
            //            System.out.println(bearerToken);

            // Users
            // Map broadcaster_name -> broadcaster_id
            // https://api.twitch.tv/helix/users
            HttpGet usersGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/users").addParameter("login", broadcasterName)
                                                                                              .build());
            usersGet.addHeader("Client-Id", clientId);
            usersGet.addHeader("Authorization", String.format("Bearer %s", bearerToken));
            CloseableHttpResponse usersGetR = httpClient.execute(usersGet);
            responseNode = getReader().readTree(usersGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());
            String broadcasterId = responseNode.get("data").get(0).get("id").asText();
            //            System.out.println(broadcasterId);

            // twitch emotes
            // channel
            // https://api.twitch.tv/helix/chat/emotes
            HttpGet twChannelEmotesGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/chat/emotes").addParameter("broadcaster_id", broadcasterId)
                                                                                                              .build());
            twChannelEmotesGet.addHeader("Client-Id", clientId);
            twChannelEmotesGet.addHeader("Authorization", String.format("Bearer %s", bearerToken));
            CloseableHttpResponse twChannelEmotesGetR = httpClient.execute(twChannelEmotesGet);
            responseNode = getReader().readTree(twChannelEmotesGetR.getEntity().getContent());
            // System.out.println(responseNode.toPrettyString());
            for (JsonNode emoteNode : responseNode.get("data")) {
                String emoteUrl = emoteNode.get("images").get("url_4x").asText();
                String emoteCode = emoteNode.get("name").asText();
                HttpGet twChannelEmoteGet = new HttpGet(emoteUrl);
                CloseableHttpResponse twChannelEmoteGetR = httpClient.execute(twChannelEmoteGet);
                //            System.out.println(twChannelEmoteGetR.getEntity().getContentType());
                String twChannelEmoteGetRType = twChannelEmoteGetR.getEntity().getContentType();
                // Default to this cause of an issue with some emote images not returning a Content-Type header
                String imageType = "png";
                if (twChannelEmoteGetRType != null) {
                    String[] contentTypeTokens = twChannelEmoteGetRType.split("/");
                    imageType = contentTypeTokens[contentTypeTokens.length - 1];
                }
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), imageType)), twChannelEmoteGetR.getEntity()
                                                                                                                                                            .getContent()
                                                                                                                                                            .readAllBytes(), StandardOpenOption.CREATE);
            }
            // global
            // https://api.twitch.tv/helix/chat/emotes/global
            HttpGet twGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.twitch.tv/helix/chat/emotes/global").build());
            twGlobalEmotesGet.addHeader("Client-Id", clientId);
            twGlobalEmotesGet.addHeader("Authorization", String.format("Bearer %s", bearerToken));
            CloseableHttpResponse twGlobalEmotesGetR = httpClient.execute(twGlobalEmotesGet);
            responseNode = getReader().readTree(twGlobalEmotesGetR.getEntity().getContent());
            // System.out.println(responseNode.toPrettyString());
            for (JsonNode emoteNode : responseNode.get("data")) {
                String emoteUrl = emoteNode.get("images").get("url_4x").asText();
                String emoteCode = emoteNode.get("name").asText();
                HttpGet twGlobalEmoteGet = new HttpGet(emoteUrl);
                CloseableHttpResponse twGlobalEmoteGetR = httpClient.execute(twGlobalEmoteGet);
                //            System.out.println(twGlobalEmoteGetR.getEntity().getContentType());
                // Default to this cause of an issue with some emote images not returning a Content-Type header
                String twGlobalEmoteGetRType = twGlobalEmoteGetR.getEntity().getContentType();
                String imageType = "png";
                if (twGlobalEmoteGetRType != null) {
                    String[] contentTypeTokens = twGlobalEmoteGetRType.split("/");
                    imageType = contentTypeTokens[contentTypeTokens.length - 1];
                }
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), imageType)), twGlobalEmoteGetR.getEntity()
                                                                                                                                                           .getContent()
                                                                                                                                                           .readAllBytes(), StandardOpenOption.CREATE);
            }

            // bttv emotes
            // https://api.betterttv.net/3/cached/users/twitch/144398644
            HttpGet bttvChannelEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/users/twitch").appendPath(broadcasterId)
                                                                                                                        .build());
            CloseableHttpResponse bttvChannelEmotesGetR = httpClient.execute(bttvChannelEmotesGet);
            responseNode = getReader().readTree(bttvChannelEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());
            // channel
            for (JsonNode channelEmoteNode : responseNode.get("channelEmotes")) {
                String emoteId = channelEmoteNode.get("id").asText();
                String emoteType = channelEmoteNode.get("imageType").asText();
                String emoteCode = channelEmoteNode.get("code").asText();
                // Download https://cdn.betterttv.net/emote/5e9e23e174046462f767475b/3x
                HttpGet bttvChannelEmoteGet = new HttpGet(new URIBuilder("https://cdn.betterttv.net/emote").appendPath(emoteId)
                                                                                                           .appendPath("3x")
                                                                                                           .build());
                CloseableHttpResponse bttvChannelEmoteGetR = httpClient.execute(bttvChannelEmoteGet);
                //            System.out.println(bttvChannelEmoteGet.getUri().toString());
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), emoteType)), bttvChannelEmoteGetR.getEntity()
                                                                                                                                                              .getContent()
                                                                                                                                                              .readAllBytes(), StandardOpenOption.CREATE);
            }
            // shared
            for (JsonNode sharedEmoteNode : responseNode.get("sharedEmotes")) {
                String emoteId = sharedEmoteNode.get("id").asText();
                String emoteType = sharedEmoteNode.get("imageType").asText();
                String emoteCode = sharedEmoteNode.get("code").asText();
                // Download https://cdn.betterttv.net/emote/5e9e23e174046462f767475b/3x
                HttpGet bttvChannelEmoteGet = new HttpGet(new URIBuilder("https://cdn.betterttv.net/emote").appendPath(emoteId)
                                                                                                           .appendPath("3x")
                                                                                                           .build());
                CloseableHttpResponse bttvChannelEmoteGetR = httpClient.execute(bttvChannelEmoteGet);
                //            System.out.println(bttvChannelEmoteGet.getUri().toString());
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), emoteType)), bttvChannelEmoteGetR.getEntity()
                                                                                                                                                              .getContent()
                                                                                                                                                              .readAllBytes(), StandardOpenOption.CREATE);
            }
            // global
            // https://api.betterttv.net/3/cached/emotes/global
            HttpGet bttvGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/emotes/global").build());
            CloseableHttpResponse bttvGlobalEmotesGetR = httpClient.execute(bttvGlobalEmotesGet);
            responseNode = getReader().readTree(bttvGlobalEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());
            for (JsonNode emoteNode : responseNode) {
                String emoteId = emoteNode.get("id").asText();
                String emoteType = emoteNode.get("imageType").asText();
                String emoteCode = emoteNode.get("code").asText();
                // Download https://cdn.betterttv.net/emote/5e9e23e174046462f767475b/3x
                HttpGet bttvGlobalEmoteGet = new HttpGet(new URIBuilder("https://cdn.betterttv.net/emote").appendPath(emoteId)
                                                                                                          .appendPath("3x")
                                                                                                          .build());
                CloseableHttpResponse bttvGlobalEmoteGetR = httpClient.execute(bttvGlobalEmoteGet);
                //            System.out.println(bttvGlobalEmoteGet.getUri().toString());
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), emoteType)), bttvGlobalEmoteGetR.getEntity()
                                                                                                                                                             .getContent()
                                                                                                                                                             .readAllBytes(), StandardOpenOption.CREATE);
            }

            // ffz emotes
            // channel
            // https://api.betterttv.net/3/cached/frankerfacez/users/twitch/144398644
            HttpGet ffzChannelEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/frankerfacez/users/twitch").appendPath(broadcasterId)
                                                                                                                                    .build());
            CloseableHttpResponse ffzChannelEmotesGetR = httpClient.execute(ffzChannelEmotesGet);
            responseNode = getReader().readTree(ffzChannelEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());

            for (JsonNode emoteNode : responseNode) {
                String ffzEmoteCode = emoteNode.get("code").asText();
                String ffzEmoteUrl = emoteNode.get("images").get("4x").asText();
                String ffzEmoteType = emoteNode.get("imageType").asText();

                HttpGet ffzChannelEmoteGet = new HttpGet(ffzEmoteUrl);
                CloseableHttpResponse ffzChannelEmoteGetR = httpClient.execute(ffzChannelEmoteGet);
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(ffzEmoteCode), ffzEmoteType)), ffzChannelEmoteGetR.getEntity()
                                                                                                                                                                   .getContent()
                                                                                                                                                                   .readAllBytes(), StandardOpenOption.CREATE);
            }
            // global
            // https://api.betterttv.net/3/cached/frankerfacez/emotes/global
            HttpGet ffzGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.betterttv.net/3/cached/frankerfacez/emotes/global").build());
            CloseableHttpResponse ffzGlobalEmotesGetR = httpClient.execute(ffzGlobalEmotesGet);
            responseNode = getReader().readTree(ffzGlobalEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());

            for (JsonNode emoteNode : responseNode) {
                String ffzEmoteCode = emoteNode.get("code").asText();
                String ffzEmoteType = emoteNode.get("imageType").asText();
                String ffzEmoteUrl = null;

                for (int imDimension : new int[]{4, 2, 1}) {
                    if (!emoteNode.get("images").path(String.format("%dx", imDimension)).isNull()) {
                        ffzEmoteUrl = emoteNode.get("images").get(String.format("%dx", imDimension)).asText();
                        break;
                    }
                }

                if (ffzEmoteUrl != null) {
                    HttpGet ffzGlobalEmoteGet = new HttpGet(ffzEmoteUrl);
                    CloseableHttpResponse ffzGlobalEmoteGetR = httpClient.execute(ffzGlobalEmoteGet);
                    Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(ffzEmoteCode), ffzEmoteType)), ffzGlobalEmoteGetR.getEntity()
                                                                                                                                                                      .getContent()
                                                                                                                                                                      .readAllBytes(), StandardOpenOption.CREATE);
                }
            }

            // 7tv emotes
            // channel
            // https://api.7tv.app/v2/users/144398644/emotes
            HttpGet _7tvChannelEmotesGet = new HttpGet(new URIBuilder("https://api.7tv.app/v2/users").appendPath(broadcasterId)
                                                                                                     .appendPath("emotes")
                                                                                                     .build());
            CloseableHttpResponse _7tvChannelEmotesGetR = httpClient.execute(_7tvChannelEmotesGet);
            responseNode = getReader().readTree(_7tvChannelEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());

            for (JsonNode emoteNode : responseNode) {
                String emoteCode = emoteNode.get("name").asText();
                String mimeString = emoteNode.get("mime").asText();
                String[] mimeTokens = mimeString.split("/");
                String emoteType = mimeTokens[mimeTokens.length - 1];
                String emoteUrl = emoteNode.get("urls").get(emoteNode.get("urls").size() - 1).get(1).asText();

                HttpGet _7tvChannelEmoteGet = new HttpGet(emoteUrl);
                CloseableHttpResponse _7tvChannelEmoteGetR = httpClient.execute(_7tvChannelEmoteGet);
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), emoteType)), _7tvChannelEmoteGetR.getEntity()
                                                                                                                                                              .getContent()
                                                                                                                                                              .readAllBytes(), StandardOpenOption.CREATE);
            }
            // global
            // https://api.7tv.app/v2/emotes/global
            HttpGet _7tvGlobalEmotesGet = new HttpGet(new URIBuilder("https://api.7tv.app/v2/emotes/global").build());
            CloseableHttpResponse _7tvGlobalEmotesGetR = httpClient.execute(_7tvGlobalEmotesGet);
            responseNode = getReader().readTree(_7tvGlobalEmotesGetR.getEntity().getContent());
            //            System.out.println(responseNode.toPrettyString());

            for (JsonNode emoteNode : responseNode) {
                String emoteCode = emoteNode.get("name").asText();
                String mimeString = emoteNode.get("mime").asText();
                String[] mimeTokens = mimeString.split("/");
                String emoteType = mimeTokens[mimeTokens.length - 1];
                String emoteUrl = emoteNode.get("urls").get(emoteNode.get("urls").size() - 1).get(1).asText();

                HttpGet _7tvGlobalEmoteGet = new HttpGet(emoteUrl);
                CloseableHttpResponse _7tvGlobalEmoteGetR = httpClient.execute(_7tvGlobalEmoteGet);
                Files.write(Paths.get(outputDirectoryPath, String.format("%s.%s", transformEmoteCodeToValidFName(emoteCode), emoteType)), _7tvGlobalEmoteGetR.getEntity()
                                                                                                                                                             .getContent()
                                                                                                                                                             .readAllBytes(), StandardOpenOption.CREATE);
            }
            long endTime = System.currentTimeMillis();
            System.out.printf("End time: %d%n", endTime);
            System.out.printf("Total time: %d", (endTime - startTime) / 1000);
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
        execService = Executors.newFixedThreadPool(16);
        return execService;
    }

    private static CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
        }
        return httpClient;
    }

    private static String transformEmoteCodeToValidFName(String emoteCode) {
        return INVALID_FILENAME_CHAR_PATTERN.matcher(emoteCode)
                                            .find() ? RandomStringUtils.randomAlphanumeric(8) : emoteCode;
    }
}
