package net.hypixel.nerdbot.util;

import com.google.gson.JsonObject;
import io.prometheus.client.Summary;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.gson.HypixelPlayerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
public class Util {

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final DecimalFormat COMMA_SEPARATED_FORMAT = new DecimalFormat("#,###");

    // UUID Pattern Matching
    public static final Pattern UUID_REGEX = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}");
    public static final Pattern TRIMMED_UUID_REGEX = Pattern.compile("[a-f0-9]{12}4[a-f0-9]{3}[89aAbB][a-f0-9]{15}");
    private static final Pattern ADD_UUID_HYPHENS_REGEX = Pattern.compile("([a-f0-9]{8})([a-f0-9]{4})(4[a-f0-9]{3})([89aAbB][a-f0-9]{3})([a-f0-9]{12})");

    private Util() {
    }

    public static List<String> splitString(String text, int size) {
        List<String> parts = new ArrayList<>();

        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(text.length(), i + size)));
        }

        return parts;
    }

    public static Stream<String> safeArrayStream(String[]... arrays) {
        Stream<String> stream = Stream.empty();

        if (arrays != null) {
            for (String[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static Stream<Object> safeArrayStream(Object[]... arrays) {
        Stream<Object> stream = Stream.empty();

        if (arrays != null) {
            for (Object[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static void sleep(TimeUnit unit, long time) {
        try {
            Thread.sleep(unit.toMillis(time));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static Guild getGuild(String guildId) {
        return NerdBotApp.getBot().getJDA().getGuildById(guildId);
    }

    @NotNull
    public static Guild getMainGuild() {
        return Objects.requireNonNull(NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()));
    }

    public static File createTempFile(String fileName, String content) throws IOException {
        String dir = System.getProperty("java.io.tmpdir");
        File file = new File(dir + File.separator + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Created temporary file " + file.getAbsolutePath());
        return file;
    }

    /**
     * Remove all reactions from a message by a user or bot
     *
     * @param reaction The {@link MessageReaction} to search for the list of users to remove
     * @param users    The {@link List list} of {@link User users} to remove the reaction from
     */
    public static int getReactionCountExcludingList(MessageReaction reaction, List<User> users) {
        return (int) reaction.retrieveUsers()
            .stream()
            .filter(user -> !users.contains(user))
            .count();
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name) {
        return getTagByName(forumChannel, name, true);
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .filter(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)))
            .findFirst()
            .orElseThrow();
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name) {
        return hasTagByName(forumChannel, name, true);
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name) {
        return hasTagByName(threadChannel, name, true);
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name, boolean ignoreCase) {
        return threadChannel.getAppliedTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getFirstLine(Message message) {
        String firstLine = message.getContentRaw().split("\n")[0];

        if (firstLine.equals("")) {
            if (message.getEmbeds().get(0).getTitle() != null) {
                firstLine = message.getEmbeds().get(0).getTitle();
            } else {
                firstLine = "No Title Found";
            }
        }

        return (firstLine.length() > 30) ? firstLine.substring(0, 27) + "..." : firstLine;
    }

    public static JsonObject makeHttpRequest(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format(url))).GET().build();
        String requestResponse;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        requestResponse = response.body();

        return NerdBotApp.GSON.fromJson(requestResponse, JsonObject.class);
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    @Deprecated
    private static final String REGEX = "^[a-zA-Z0-9_]{2,16}";
    @Deprecated
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    @Deprecated
    public static Optional<String> getScuffedMinecraftIGN(Member member) {
        // removes non-standard ascii characters from the discord nickname
        String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
        String memberMCUsername = null;

        // checks if the member's username has flair
        if (!Pattern.matches(REGEX, plainUsername)) {
            // removes start and end characters ([example], {example}, |example| or (example)).
            // also strips spaces from the username
            plainUsername = plainUsername.replaceAll(SURROUND_REGEX, "").replace(" ", "");
            String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

            // gets the first item that matches the name constraints
            for (String item : splitUsername) {
                if (Pattern.matches(REGEX, item)) {
                    memberMCUsername = item;
                    break;
                }
            }
        } else {
            memberMCUsername = plainUsername.replace(" ", "");
        }

        return Optional.ofNullable(memberMCUsername);
    }

    public static MojangProfile getMojangProfile(String username) throws HttpException {
        String url = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);
        MojangProfile mojangProfile;
        int statusCode;

        try (Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer()) {
            HttpResponse<String> httpResponse = getHttpResponse(url);
            statusCode = httpResponse.statusCode();
            mojangProfile = NerdBotApp.GSON.fromJson(httpResponse.body(), MojangProfile.class);

            requestTimer.observeDuration();
        } catch (Exception ex) {
            throw new HttpException("Failed to request Mojang Profile for `" + username + "`: " + ex.getMessage(), ex);
        }

        if (statusCode != 200) {
            throw new HttpException("Failed to request Mojang Profile for `" + username + "`: " + mojangProfile.getErrorMessage());
        }

        return mojangProfile;
    }

    @NotNull
    public static MojangProfile getMojangProfile(UUID uniqueId) throws HttpException {
        String url = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", uniqueId.toString());
        MojangProfile mojangProfile;
        int statusCode;

        try (Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer()) {
            HttpResponse<String> httpResponse = getHttpResponse(url);
            statusCode = httpResponse.statusCode();
            mojangProfile = NerdBotApp.GSON.fromJson(httpResponse.body(), MojangProfile.class);

            requestTimer.observeDuration();
        } catch (Exception ex) {
            throw new HttpException("Unable to locate Minecraft Username for `" + uniqueId + "`", ex);
        }

        if (statusCode != 200) {
            throw new HttpException("Failed to request Mojang Profile for `" + uniqueId + "`: " + mojangProfile.getErrorMessage());
        }

        return mojangProfile;
    }

    private static HttpResponse<String> getHttpResponse(String url, Pair<String, String>... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        Arrays.asList(headers).forEach(pair -> builder.header(pair.getLeft(), pair.getRight()));
        HttpRequest request = builder.build();
        log.info("Sending HTTP request to " + url + " with headers " + Arrays.toString(headers));
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HypixelPlayerResponse getHypixelPlayer(UUID uniqueId) throws HttpException {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId.toString());
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        try {
            String hypixelApiKey = NerdBotApp.getHypixelAPIKey().map(UUID::toString).orElse("");
            return NerdBotApp.GSON.fromJson(getHttpResponse(url, Pair.of("API-Key", hypixelApiKey)).body(), HypixelPlayerResponse.class);
        } catch (Exception ex) {
            throw new HttpException("Unable to locate Hypixel Player for `" + uniqueId + "`", ex);
        } finally {
            requestTimer.observeDuration();
        }
    }

    public static boolean isUUID(String input) {
        return (input != null && !input.isEmpty()) && (input.matches(UUID_REGEX.pattern()) || input.matches(TRIMMED_UUID_REGEX.pattern()));
    }

    /**
     * Converts a string representation (with or without dashes) of a UUID to the {@link UUID} class.
     *
     * @param input unique id to convert.
     *
     * @return converted unique id.
     */
    public static UUID toUUID(String input) {
        if (!isUUID(input)) {
            throw new IllegalArgumentException("Not a valid UUID!");
        }

        if (input.contains("-")) {
            return UUID.fromString(input); // Already has hyphens
        }

        return UUID.fromString(input.replaceAll(ADD_UUID_HYPHENS_REGEX.pattern(), "$1-$2-$3-$4-$5"));
    }

    public static String getDisplayName(User user) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(user.getId());

        if (discordUser.isProfileAssigned()) {
            return discordUser.getMojangProfile().getUsername();
        } else {
            Guild guild = Util.getMainGuild();
            Member sbnMember = guild.retrieveMemberById(user.getId()).complete();

            if (sbnMember == null || sbnMember.getNickname() == null) {
                return user.getEffectiveName();
            }

            return sbnMember.getNickname();
        }
    }

    /**
     * Initializes a font.
     *
     * @param path The path to the font in the resources' folder.
     *
     * @return The initialized font.
     */
    @Nullable
    public static Font initFont(String path, float size) {
        Font font;
        try (InputStream fontStream = GeneratorCommands.class.getResourceAsStream(path)) {
            if (fontStream == null) {
                log.error("Couldn't initialise font: " + path);
                return null;
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
            return null;
        }
        return font;
    }

    /**
     * Finds a matching value within a given set based on its name
     *
     * @param enumSet an array to search for the enum in
     * @param match   the value to find in the array
     *
     * @return returns the enum item or null if not found
     */
    @Nullable
    public static Enum<?> findValue(Enum<?>[] enumSet, String match) {
        for (Enum<?> enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name()))
                return enumItem;
        }

        return null;
    }
}
