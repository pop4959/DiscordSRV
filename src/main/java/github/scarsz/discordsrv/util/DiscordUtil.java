package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static github.scarsz.discordsrv.DiscordSRV.debug;
import static github.scarsz.discordsrv.DiscordSRV.warning;

/**
 * Made by Scarsz
 *
 * @in /dev/hell
 * @on 11/7/2016
 * @at 1:59 AM
 */
public class DiscordUtil {

    /**
     * Get the current JDA object that DiscordSRV is utilizing
     * @return JDA
     */
    public static JDA getJda() {
        return DiscordSRV.getPlugin().getJda();
    }

    /**
     * Get the given Role's name
     * @param role Role to get the name of
     * @return The name of the Role; if the Role is null, a blank string.
     */
    public static String getRoleName(Role role) {
        return role == null ? "" : role.getName();
    }

    /**
     * Get the top hierarchical Role of the User in the Guild
     * @param user User the get the top Role of
     * @param guild Guild that the Role should be the top of
     * @return The top hierarchical Role
     */
    public static Role getTopRole(User user, Guild guild) {
        return getTopRole(guild.getMember(user));
    }
    /**
     * Get the top hierarchical Role of the Member
     * @param member Member to get the top role of
     * @return The top hierarchical Role
     */
    public static Role getTopRole(Member member) {
        Role highestRole = null;
        for (Role role : member.getRoles()) {
            if (highestRole == null) highestRole = role;
            else if (highestRole.getPosition() < role.getPosition()) highestRole = role;
        }
        return highestRole;
    }

    /**
     * Convert @mentions into Discord-compatible <@012345678901234567890> mentions
     * @param message Message to convert
     * @param guild Guild to find names to convert
     * @return Contents of the given message with names converted to mentions
     */
    public static String convertMentionsFromNames(String message, Guild guild) {
        if (!message.contains("@")) return message;
        List<String> splitMessage = new ArrayList<>(Arrays.asList(message.split("@| ")));
        for (Member member : guild.getMembers())
            for (String segment : splitMessage)
                if (member.getEffectiveName().toLowerCase().equals(segment.toLowerCase()))
                    splitMessage.set(splitMessage.indexOf(segment), member.getAsMention());
        splitMessage.removeAll(Arrays.asList("", null));
        return String.join(" ", splitMessage);
    }

    /**
     * Return the given String with Markdown escaped. Useful for sending things to Discord.
     * @param text String to escape markdown in
     * @return String with markdown escaped
     */
    public static String escapeMarkdown(String text) {
        return text.replace("_", "\\_").replace("*", "\\*").replace("~", "\\~");
    }

    /**
     * Strip the given String of Minecraft coloring. Useful for sending things to Discord.
     * @param text the given String to strip colors from
     * @return the given String with coloring stripped
     */
    public static String stripColor(String text) {
        if (text == null) return null;

        // standard regex-powered color stripping
        String newText = stripColorPattern.matcher(text).replaceAll("");

        // nuking the fuck out of it
        newText = newText.replaceAll("[&§][0-9a-fklmnor]", "");
        newText = newText.replaceAll("\\[[0-9]{1,2};[0-9]{1,2};[0-9]{1,2}m", "");
        newText = newText.replaceAll("\\[[0-9]{1,3}m", "");
        newText = newText.replace("[m", "");

        return newText;
    }
    private static final Pattern stripColorPattern = Pattern.compile("(?i)" + String.valueOf('§') + "[0-9A-FK-OR]");

    /**
     * Send the given String message to the given TextChannel
     * @param channel Channel to send the message to
     * @param message Message to send to the channel
     */
    public static void sendMessage(TextChannel channel, String message) {
        sendMessage(channel, message, 0);
    }
    /**
     * Send the given String message to the given TextChannel that will expire in x milliseconds
     * @param channel the TextChannel to send the message to
     * @param message the message to send to the TextChannel
     * @param expiration milliseconds until expiration of message. if this is 0, the message will not expire
     */
    public static void sendMessage(TextChannel channel, String message, int expiration) {
        if (channel == null) {
            debug("Tried sending a message to a null channel");
            return;
        }

        if (getJda() == null) {
            debug("Tried sending a message using a null JDA instance");
            return;
        }

        if (!checkPermission(channel, Permission.MESSAGE_READ)) {
            debug("Tried sending a message to channel " + channel + " but the bot doesn't have read permissions for that channel");
            return;
        }

        if (!checkPermission(channel, Permission.MESSAGE_WRITE)) {
            debug("Tried sending a message to channel " + channel + " but the bot doesn't have write permissions for that channel");
            return;
        }

        if (message == null) {
            debug("Tried sending a null message to " + channel);
            return;
        }

        if (message.equals("")) {
            debug("Tried sending a blank message to " + channel);
            return;
        }

        message = DiscordUtil.stripColor(message);

        String overflow = null;
        if (message.length() > 2000) {
            warning("Tried sending message with length of " + message.length() + " (" + (message.length() - 2000) + " over limit)");
            overflow = message.substring(2000);
            message = message.substring(0, 2000);
        }

        queueMessage(channel, message, m -> {
            if (expiration > 0) {
                try { Thread.sleep(expiration); } catch (InterruptedException e) { e.printStackTrace(); }
                deleteMessage(m);
            }
        });
        if (overflow != null) sendMessage(channel, overflow, expiration);
    }

    /**
     * Check if the bot has the given permission in the given channel
     * @param channel Channel to check for the permission in
     * @param permission Permission to be checked for
     * @return true if the permission is obtained, false otherwise
     */
    public static boolean checkPermission(Channel channel, Permission permission) {
        return checkPermission(channel, getJda().getSelfUser(), permission);
    }
    /**
     * Check if the given user has the given permission in the given channel
     * @param channel Channel to check for the permission in
     * @param user User to check permissions for
     * @param permission Permission to be checked for
     * @return true if the permission is obtained, false otherwise
     */
    public static boolean checkPermission(Channel channel, User user, Permission permission) {
        if (channel == null) return false;
        return channel.getGuild().getMember(user).hasPermission(channel, permission);
    }

    /**
     * Send the given message to the given channel, blocking the thread's execution until it's successfully sent then returning it
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     * @return The sent message
     */
    public static Message sendMessageBlocking(TextChannel channel, String message) {
        return sendMessageBlocking(channel, new MessageBuilder().append(message).build());
    }
    /**
     * Send the given message to the given channel, blocking the thread's execution until it's successfully sent then returning it
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     * @return The sent message
     */
    public static Message sendMessageBlocking(TextChannel channel, Message message) {
        if (channel == null) {
            debug("Tried sending a message to a null channel");
            return null;
        }

        if (!DiscordUtil.checkPermission(channel, Permission.MESSAGE_READ)) {
            debug("Tried sending a message to channel " + channel + " of which the bot doesn't have read permission for");
            return null;
        }
        if (!DiscordUtil.checkPermission(channel, Permission.MESSAGE_WRITE)) {
            debug("Tried sending a message to channel " + channel + " of which the bot doesn't have write permission for");
            return null;
        }

        try {
            return channel.sendMessage(message).block();
        } catch (RateLimitedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Send the given message to the given channel
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     */
    public static void queueMessage(TextChannel channel, String message) {
        queueMessage(channel, new MessageBuilder().append(message).build());
    }
    /**
     * Send the given message to the given channel
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     */
    public static void queueMessage(TextChannel channel, Message message) {
        queueMessage(channel, message, null);
    }
    /**
     * Send the given message to the given channel, optionally doing something with the message via the given consumer
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     * @param consumer The consumer to handle the message
     */
    public static void queueMessage(TextChannel channel, String message, Consumer<Message> consumer) {
        queueMessage(channel, new MessageBuilder().append(message).build(), consumer);
    }
    /**
     * Send the given message to the given channel, optionally doing something with the message via the given consumer
     * @param channel The channel to send the message to
     * @param message The message to send to the channel
     * @param consumer The consumer to handle the message
     */
    public static void queueMessage(TextChannel channel, Message message, Consumer<Message> consumer) {
        if (channel == null) {
            debug("Tried sending a message to a null channel");
            return;
        }

        if (!DiscordUtil.checkPermission(channel, Permission.MESSAGE_READ)) {
            debug("Tried sending a message to channel " + channel + " of which the bot doesn't have read permission for");
            return;
        }
        if (!DiscordUtil.checkPermission(channel, Permission.MESSAGE_WRITE)) {
            debug("Tried sending a message to channel " + channel + " of which the bot doesn't have write permission for");
            return;
        }

        try {
            channel.sendMessage(message).queue(consumer);
        } catch (IllegalStateException ignored) {}
    }

    /**
     * Set the topic message of the given channel
     * @param channel The channel to set the topic of
     * @param topic The new topic to be set
     */
    public static void setTextChannelTopic(TextChannel channel, String topic) {
        if (channel == null) {
            debug("Attempted to set status of null channel");
            return;
        }

        if (!DiscordUtil.checkPermission(channel, Permission.MANAGE_CHANNEL)) {
            warning("Unable to update topic of " + channel + " because the bot is missing the \"Manage Channel\" permission. Did you follow the instructions?");
            return;
        }

        try {
            channel.getManager().setTopic(topic).queue();
        } catch (IllegalStateException ignored) {}
    }

    /**
     * Set the game status of the bot
     * @param gameStatus The game status to be set
     */
    public static void setGameStatus(String gameStatus) {
        if (getJda() == null) {
            debug("Attempted to set game status using null JDA");
            return;
        }
        if (gameStatus == null || gameStatus.isEmpty()) {
            debug("Attempted setting game status to a null or empty string");
            return;
        }

        getJda().getPresence().setGame(Game.of(gameStatus));
    }

    /**
     * Delete the given message, given the bot has permission to
     * @param message The message to delete
     */
    public static void deleteMessage(Message message) {
        if (message.isFromType(ChannelType.PRIVATE)) return;

        if (!checkPermission(message.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            warning("Could not delete message in channel " + message.getTextChannel() + ", no permission to manage messages");
            return;
        }

        message.deleteMessage().queue();
    }

    /**
     * Open the private channel for the given user and send them the given message
     * @param user User to send the message to
     * @param message Message to send to the user
     */
    public static void privateMessage(User user, String message) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message).queue());
    }

}