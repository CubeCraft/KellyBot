package net.cubecraftgames.kelly;

import org.apache.abdera.Abdera;
import org.pircbotx.*;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.UserListEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Kelly extends ListenerAdapter<PircBotX> {
	
	public static void main(String[] args) {
		new Kelly();
	}
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private MultiBotManager<PircBotX> multiBotManager;
	private Map<String, Channel> channels = new HashMap<>();
	private Map<String, FeedContainer> feeds = new HashMap<>();
	private ScheduledExecutorService scheduler;
    private Abdera abdera;
	
	@SuppressWarnings("unchecked")
	public Kelly() {
        logger.info("Checking config...");
		File configFile = new File("./config.yml");
		if (!configFile.exists()) {
            logger.info("Config does not exist! Creating default config...");
			try {
				configFile.createNewFile();
				Scanner scanner = new Scanner(getClass().getResourceAsStream("/config.yml"));
				FileWriter fileWriter = new FileWriter(configFile);
				while (scanner.hasNextLine()) {
					fileWriter.write(scanner.nextLine() + '\n');
				}
				fileWriter.close();
				scanner.close();
                logger.info("Finished writing default config.");
			} catch (IOException exception) {
				exception.printStackTrace();
			}
		}
        logger.info("Creating Abdera instance...");
        abdera = new Abdera();
		multiBotManager = new MultiBotManager<>();
		Yaml yaml = new Yaml();
		try {
			Map<String, Object> config = (Map<String, Object>) yaml.load(new FileInputStream(configFile));
			for (String hostName : ((Map<String, Object>) config.get("servers")).keySet()) {
				logger.info("Creating bot for " + hostName + "...");
				Configuration.Builder<PircBotX> botConfigBuilder = new Configuration.Builder<PircBotX>();
	            if (config.get("name") != null) {
	            	String name = (String) config.get("name");
	            	botConfigBuilder.setName(name);
	            	logger.info("Name set to " + name);
	            }
				if (config.get("real-name") != null) {
					String realName = (String) config.get("real-name");
					botConfigBuilder.setRealName(realName);
					logger.info("Real name set to " + realName);
				}
				if (config.get("login") != null) {
					String login = (String) config.get("login");
					botConfigBuilder.setLogin(login);
					logger.info("Login set to " + login);
				}
				if (config.get("cap-enabled") != null) {
					boolean capEnabled = (boolean) config.get("cap-enabled");
					botConfigBuilder.setCapEnabled(capEnabled);
					logger.info("CAP " + (capEnabled ? "enabled" : "disabled"));
				}
				if (config.get("auto-nick-change") != null) {
					boolean autoNickChange = (boolean) config.get("auto-nick-change");
					botConfigBuilder.setAutoNickChange(autoNickChange);
					logger.info("Auto nick change " + (autoNickChange ? "enabled" : "disabled"));
				}
				if (config.get("auto-split-message") != null) {
					boolean autoSplitMessage = (boolean) config.get("auto-split-message");
					botConfigBuilder.setAutoSplitMessage(autoSplitMessage);
					logger.info("Auto-split message " + (autoSplitMessage ? "enabled" : "disabled"));
				}
				botConfigBuilder.setServerHostname(hostName);
				if (config.get("max-line-length") != null) {
					int maxLineLength = (int) config.get("max-line-length");
					botConfigBuilder.setMaxLineLength(maxLineLength);
					logger.info("Max line length set to " + maxLineLength);
				}
				if (config.get("message-delay") != null) {
					long messageDelay = Long.parseLong("" + (int) config.get("message-delay"));
					botConfigBuilder.setMessageDelay(messageDelay);
					logger.info("Message delay set to " + messageDelay);
				}
				if (config.get("password") != null) {
					String password = (String) config.get("password");
					botConfigBuilder.setNickservPassword(password);
					logger.info("Password set. (" + password.length() + " characters, starting with " + password.charAt(0) + ", ending with " + password.charAt(password.length() - 1) + ")");
				}
				for (String channel : ((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) config.get("servers")).get(hostName)).get("channels")).keySet()) {
					botConfigBuilder.addAutoJoinChannel(channel);
					logger.info("Added channel: " + channel);
					for (Entry<String, Object> entry : ((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) config.get("servers")).get(hostName)).get("channels")).entrySet()) {
	                    URL url = null;
	                    if (entry.getValue() != null) {
		                    try {
		                        url = new URL((String) ((Map<String, Object>) entry.getValue()).get("url"));
		                    } catch (MalformedURLException exception) {
		                        exception.printStackTrace();
		                    }
                            FeedType type = null;
                            switch (((String) ((Map<String, Object>) entry.getValue()).get("type"))) {
                                case "rss": type = FeedType.RSS; break;
                                case "atom": type = FeedType.ATOM; break;
                            }
                            if (type != null) {
                                feeds.put(entry.getKey(), new FeedContainer(abdera, url, type));
                                logger.info("Added " + type.toString() + " feed to " + channel + ": " + url.toString());
                            }
	                    }
					}
				}
                botConfigBuilder.setAutoReconnect(true);
				botConfigBuilder.addListener(this);
				logger.info("Building configuration...");
				long configBuildStartTime = System.currentTimeMillis();
				multiBotManager.addBot(botConfigBuilder.buildConfiguration());
				logger.info("Done! (" + (System.currentTimeMillis() - configBuildStartTime) + "ms)");
			}
			logger.info("Starting bots...");
			long botStartStartTime = System.currentTimeMillis();
			multiBotManager.start();
			logger.info("Done! (" + (System.currentTimeMillis() - botStartStartTime) + "ms)");
			logger.info("Scheduling feed polling...");
			long schedulingStartTime = System.currentTimeMillis();
			scheduler = Executors.newScheduledThreadPool(1);
			scheduler.scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
					for (Channel channel : Kelly.this.channels.values()) {
						if (!Kelly.this.hasSentLatestMessage(channel)) {
							Kelly.this.sendLatestFeedMessage(channel);
							updateLatestSentMessage(channel);
						}
					}
				}
				
			}, (int) config.get("startup-delay"), (int) config.get("check-rss-delay"), TimeUnit.SECONDS);
			logger.info("Done! (" + (System.currentTimeMillis() - schedulingStartTime) + "ms)");
		} catch (FileNotFoundException exception) {
			exception.printStackTrace();
		}
    }
	
	@Override
	public void onUserList(UserListEvent<PircBotX> event) {
        logger.info("User list recieved for " + event.getChannel().getName());
		channels.put(event.getChannel().getName(), event.getChannel());
	}
	
	@Override
	public void onMessage(MessageEvent<PircBotX> event) {
		if (event.getMessage().startsWith("!feed")) {
            logger.info("Command detected: !feed");
			sendFeedMessages(event.getChannel(), event.getUser());
		}
	}
	
	public void sendFeedMessages(Channel channel) {
        logger.info("Sending all feed messages to " + channel.getName() + "...");
		for (FeedMessageContainer message : feeds.get(channel.getName()).getMessages()) {
			channel.send().message(formatMessage(message));
		}
	}
	
	public void sendFeedMessages(Channel channel, User user) {
        logger.info("Sending all feed messages for " + channel.getName() + " to " + user.getNick() + "...");
		for (FeedMessageContainer message : feeds.get(channel.getName()).getMessages()) {
			user.send().message(formatMessage(message));
		}
	}
	
	public void sendLatestFeedMessage(Channel channel) {
        logger.info("Sending latest feed message to " + channel.getName() + "...");
		FeedMessageContainer message = feeds.get(channel.getName()).getMessages().get(0);
		channel.send().message(formatMessage(message));
	}
	
	public boolean hasSentLatestMessage(Channel channel) {
        logger.info("Checking last sent message...");
		File latestMessageFile = new File("./latest-message-" + channel.getName());
		if (latestMessageFile.exists()) {
			try {
				Scanner scanner = new Scanner(new FileInputStream(latestMessageFile));
				String latestMessage = scanner.nextLine();
				scanner.close();
                logger.info("Read last sent message.");
				return latestMessage.equals(feeds.get(channel.getName()).getMessages().get(0).getLink());
			} catch (FileNotFoundException exception) {
				exception.printStackTrace();
			}
		}
		return false;
	}
	
	public void updateLatestSentMessage(Channel channel) {
        logger.info("Updating latest sent message file...");
		File latestMessageFile = new File("./latest-message-" + channel.getName());
		try {
			FileWriter fileWriter = new FileWriter(latestMessageFile);
			fileWriter.write(feeds.get(channel.getName()).getMessages().get(0).getLink());
			fileWriter.close();
            logger.info("File rewritten.");
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}
	
	public Channel getChannel(String name) {
		return channels.get(name);
	}

    private String formatMessage(FeedMessageContainer message) {
        logger.info("Loading config...");
        Yaml yaml = new Yaml();
        File configFile = new File("./config.yml");
        Map<String, Object> config = null;
        try {
            config = (Map<String, Object>) yaml.load(new FileInputStream(configFile));
        } catch (FileNotFoundException exception) {
            exception.printStackTrace();
        }
        logger.info("Formatting new feed entry...");
        return ((String) config.get("message-format")).replace("%title", message.getTitle()).replace("%description", message.getDescription()).replace("%link", message.getLink()).replace("%author", message.getAuthor());
    }

}
