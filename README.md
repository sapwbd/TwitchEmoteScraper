# Twitch Emote Scraper
JAR executable to dump global and broadcaster specific twitch as well third-party (BTTV, FFZ, 7TV) emotes

## Usage

### Pre-requisites

[Java version 18+](https://docs.oracle.com/en/java/javase/18/install/installation-jdk-microsoft-windows-platforms.html#GUID-A7E27B90-A28D-4237-9383-A58B416071CA)

### Steps
Login to the [twitch developer console](https://dev.twitch.tv/console/apps) using any valid twitch account, register a mock
application to obtain a _clientId_ and _clientSecret_ for use with this application. Follow the instructions at
[Register an Application](https://dev.twitch.tv/docs/api/get-started/#register-an-application) section of API docs for additional help

Go to the [releases](https://github.com/sapwbd/TwitchEmoteScraper/releases) section of the repository and download the latest
version asset _TwitchEmoteScraper-{version}-jar-with-dependencies.jar_. Alternatively follow the build instructions
in the next section

Open your terminal of choice, switch to the directory where you have downloaded the releases artifact and execute the
runnable JAR

```
java -jar TwitchEmoteScraper-{version}-jar-with-dependencies.jar {clientId} {clientSecret} {broadcasterName} {outputDirectory}
```
where,  
_clientId_ -> Obtained from the twitch developer console after registering your mock application  
_clientSecret_ -> Obtained from the twitch developer console after registering your mock application  
_broadcasterName_ -> Twitch broadcaster profile name  
_outputDirectory_ -> Output directory for the emotes

## Building the artifact

Clone the repository to your local file system

Open a terminal of choice and switch to the repository directory. Run the following command

```
.\{mvnwBinary} clean package
```

where,  
_mvnwBinary_ is either the mvnw or mvnw.cmd if your OS is linux based or Windows

The final artifact will be present in the target subdirectory as _TwitchEmoteScraper-{version}-jar-with-dependencies.jar_
