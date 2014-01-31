package net.cubecraftgames.kelly;

import net.cubecraftgames.kelly.rss.FeedMessage;
import net.cubecraftgames.kelly.rss.RSSFeedParser;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FeedContainer {

    private Abdera abdera;
    private URL url;
    private FeedType feedType;

    public FeedContainer(Abdera abdera, URL url, FeedType type) {
        this.abdera = abdera;
        this.url = url;
        this.feedType = type;
    }

    public URL getUrl() {
        return url;
    }

    public FeedType getFeedType() {
        return feedType;
    }

    public List<FeedMessageContainer> getMessages() {
        switch (feedType) {
            case RSS: return getMessagesRss();
            case ATOM: return getMessagesAtom();
        }
        return new ArrayList<>();
    }

    private List<FeedMessageContainer> getMessagesRss() {
        List<FeedMessageContainer> messages = new ArrayList<>();
        RSSFeedParser parser = new RSSFeedParser(url);
        for (FeedMessage message : parser.readFeed().getMessages()) {
            messages.add(new FeedMessageContainer(message));
        }
        return messages;
    }

    private List<FeedMessageContainer> getMessagesAtom() {
        List<FeedMessageContainer> messages = new ArrayList<>();
        Parser parser = abdera.getParser();
        Document<Feed> doc = null;
        try {
            doc = parser.parse(url.openStream(),url.toString());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        Feed feed = doc.getRoot();
        for (Entry entry : feed.getEntries()) {
            messages.add(new FeedMessageContainer(entry));
        }
        return messages;
    }

}
