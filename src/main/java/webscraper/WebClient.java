package webscraper;

import com.bericotech.clavin.gazetteer.GeoName;
import com.bericotech.clavin.resolver.ResolvedLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import solrapi.model.IndexedNews;
import solrapi.model.IndexedStock;
import geoparsing.LocationResolver;

import common.DetectHtml;
import common.Tools;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
//import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
//import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
//import org.deeplearning4j.models.word2vec.VocabWord;
//import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
//import org.deeplearning4j.text.documentiterator.LabelsSource;
//import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
//import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
//import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
//import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
//import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebClient {

    final static Logger logger = LogManager.getLogger(WebClient.class);

    private String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";

    private final ArticleExtractor articleExtractor;
    private final NamedEntityRecognizer ner;
    private final PorterStemmer stemmer;
    private final SentenceModel sentModel;
    private final LocationResolver locationResolver;
    private final SolrClient solrClient;
    //private final EventCategorizer categorizer;

    private static final int REQUEST_DELAY = 30000;

    public static final String QUERY_TIMEFRAME_RECENT = "";
    public static final String QUERY_TIMEFRAME_LAST_HOUR = "qdr:h";
    public static final String QUERY_TIMEFRAME_ALL_ARCHIVED = "ar:1";
    public static final String QUERY_BLOGS = "nrt:b";

    private String symbol;

    public static void main(String[] args) {
        WebClient client = new WebClient();
        //past hour
        client.queryGoogle("NASDAQ:AMZN", QUERY_TIMEFRAME_LAST_HOUR, client::processSearchResult);
        //archives
        //client.queryGoogle(QUERY_TIMEFRAME_ALL_ARCHIVED, client::gatherData);
        //client.queryGoogle(QUERY_TIMEFRAME_LAST_HOUR, client::gatherData);
    }

    public WebClient() {
        articleExtractor = new ArticleExtractor();
        ner = new NamedEntityRecognizer();
        stemmer = new PorterStemmer();
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        locationResolver = new LocationResolver();
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        //categorizer = new EventCategorizer(solrClient);
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int queryGoogle(String searchTerm, String timeFrameSelector, BiConsumer<Document, Element> action) {
        int totalArticles = 0;
        try {
            int start = 0;

            logger.info("Searching Google for: " + searchTerm);
            Elements results = getGoogleSearchResults(searchTerm, timeFrameSelector, start);
            if (results != null && results.eachText() != null) {
                while (results.eachText().size() > 0) {
                    int resultsSize = results.eachText().size();
                    totalArticles += resultsSize;
                    for (Element result : results){
                        String href = result.attr("href");
                        try {
                            logger.info("Scraping data from: " + href);
                            Document article = Jsoup.connect(href).userAgent(USER_AGENT).get();
                            action.accept(article, result);
                            logger.info("Successfully scraped data from: " + href);
                        }
                        catch (Exception e) {
                            logger.info("Failed to scrape data from: " + href);
                            logger.error(e.getMessage(), e);
                        }
                    }
                    if (resultsSize >= 10) {
                        start += resultsSize;
                        Thread.sleep(REQUEST_DELAY);
                        logger.info("Getting next page of Google results for: " + searchTerm);
                        results = getGoogleSearchResults(searchTerm, timeFrameSelector, start);
                    } else {
                        break;
                    }
                }
            }
            Thread.sleep(REQUEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return totalArticles;
    }

    public void processSearchResult(Document article, Element result) {
        IndexedNews news = IndexedNews.consume(article, result, articleExtractor);
        news.setSymbol(symbol);
        news.initId();

        if (news != null) {
            //TODO: detect news category and sentiment
            try {
                solrClient.indexDocument(news);
            } catch (SolrServerException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private Elements getGoogleSearchResults(String queryTerm, String timeFrameSelector, int start) {
        Document doc = null;
        try {
            if (!queryTerm.startsWith("#")) {
                doc = Jsoup.connect("https://www.google.com/search?q=" + queryTerm + "&cr=countryUS&lr=lang_en&tbas=0&tbs=sbd:1," + timeFrameSelector + ",lr:lang_1en,ctr:countryUS&tbm=nws&start=" + start)
                        .userAgent(USER_AGENT)
                        .get();
            } else {
                return null;
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }

        Elements results = doc.select("h3.r a");

        return results;
    }
}
