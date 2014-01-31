package net.cubecraftgames.kelly;

import net.cubecraftgames.kelly.rss.FeedMessage;
import org.apache.abdera.model.Entry;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

public class FeedMessageContainer {

    private String title;
    private String description;
    private String link;
    private String author;

    public FeedMessageContainer(FeedMessage message) {
        this.title = message.getTitle();
        this.description = message.getDescription();
        this.link = message.getLink();
        this.author = message.getAuthor();
    }

    public FeedMessageContainer(Entry entry) {
        this.title = entry.getTitle();
        this.description = entry.getContent();
        try {
            this.link = entry.getBaseUri().toURL().toString();
        } catch (MalformedURLException exception) {
            exception.printStackTrace();
        } catch (URISyntaxException exception) {
            exception.printStackTrace();
        }
        this.author = entry.getAuthor().getName();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

}
