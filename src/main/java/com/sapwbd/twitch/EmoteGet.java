package com.sapwbd.twitch;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;


public class EmoteGet implements Runnable {

    private static final ThreadLocal<CloseableHttpClient> tLocal = new ThreadLocal<>();
    public static final Pattern INVALID_FILENAME_CHAR_PATTERN = Pattern.compile("[^\\w.-]");

    // Fields
    private final String emoteUrl;
    private final String emoteCode;
    private final String outputDirectory;
    private String emoteType;

    // Constructor
    public EmoteGet(String emoteUrl, String emoteCode, String emoteType, String outputDirectory) {
        this.emoteUrl = emoteUrl;
        this.emoteCode = emoteCode;
        this.emoteType = emoteType;
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void run() {
        CloseableHttpClient httpClient = tLocal.get();
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
            tLocal.set(httpClient);
        }

        try {
            CloseableHttpResponse emoteGetR = httpClient.execute(new HttpGet(emoteUrl));
            if (emoteType == null) {
                emoteType = "png";
                if (emoteGetR.getEntity().getContentType() != null) {
                    String[] contentTypeTokens = emoteGetR.getEntity().getContentType().split("/");
                    emoteType = contentTypeTokens[contentTypeTokens.length - 1];
                }
            }
            if (Files.notExists(Paths.get(outputDirectory))) {
                Files.createDirectories(Paths.get(outputDirectory));
            }
            Files.write(Paths.get(outputDirectory, String.format("%s.%s", convertEmoteCodeToValidFileNameString(emoteCode), emoteType)), emoteGetR.getEntity()
                                                                                                                                                  .getContent()
                                                                                                                                                  .readAllBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertEmoteCodeToValidFileNameString(String emoteCode) {
        return INVALID_FILENAME_CHAR_PATTERN.matcher(emoteCode)
                                            .find() ? RandomStringUtils.randomAlphanumeric(8) : emoteCode;
    }
}
