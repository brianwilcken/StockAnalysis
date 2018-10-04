package webapp.controllers;

import alphavantageapi.AlphavantageClient;
import alphavantageapi.model.StockPullOperation;
import common.Tools;
import nlp.NamedEntityRecognizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solrapi.SolrClient;
import solrapi.model.IndexedNews;
import solrapi.model.IndexedStocksQueryParams;
import webapp.models.JsonResponse;
import webapp.services.RefreshStockDataService;
import webscraper.WebClient;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/stocks")
public class StocksController {

    private SolrClient solrClient;
    private WebClient webClient;
    private AlphavantageClient alphavantageClient;
    private List<Exception> exceptions;
    private NamedEntityRecognizer namedEntityRecognizer;

    final static Logger logger = LogManager.getLogger(StocksController.class);

    @Autowired
    private RefreshStockDataService refreshStockDataService;

    @Autowired
    private HttpServletRequest context;

    public StocksController() {
        webClient = new WebClient();
        alphavantageClient = new AlphavantageClient();
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        exceptions = new ArrayList<>();
        namedEntityRecognizer = new NamedEntityRecognizer(solrClient);
    }

    public WebClient getWebClient() { return webClient; }

    public AlphavantageClient getAlphaVantageClient() { return alphavantageClient; }

    @PostConstruct
    public void initRefreshStockDataProcess() {
        try {
            logger.info("Initializing stock data refresh process");
            refreshStockDataService.refreshStockData(this);
        } catch (Exception e) {}
    }

    @PostConstruct
    public void initRefreshStockNewsProcess() {
        try {
            logger.info("Initializing stock news refresh process");
            refreshStockDataService.refreshStockNews(this);
        } catch (Exception e) {}
    }

    public void refreshStockDataProcessExceptionHandler(Exception e) {
        Tools.getExceptions().add(e);
        try {
            refreshStockDataService.refreshStockData(this);
        } catch (Exception e1) {}
    }

    public void refreshStockNewsProcessExceptionHandler(Exception e) {
        Tools.getExceptions().add(e);
        try {
            refreshStockDataService.refreshStockNews(this);
        } catch (Exception e1) {}
    }

    @RequestMapping(method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStocks(IndexedStocksQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "In getStocks method");
        try {
            int totalStocks = 0;
            Map<String, StockPullOperation> stockPullOperationMap = params.getStockPullOperations();
            for (String symbol : stockPullOperationMap.keySet()) {
                totalStocks += alphavantageClient.QueryStockData(symbol, stockPullOperationMap.get(symbol));
            }
            return ResponseEntity.ok().body(Tools.formJsonResponse(totalStocks, params.getQueryTimeStamp()));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }

    @RequestMapping(path="/news", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStockNews(IndexedStocksQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "In getStockNews method");
        try {
            SolrQuery.SortClause clause = new SolrQuery.SortClause("articleDate", SolrQuery.ORDER.desc);
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, params.getQuery(), params.getQueryRows(), params.getQueryStart(), clause, params.getFilterQueries());
            for (IndexedNews news : indexedNewss) {
                news.setParsed(namedEntityRecognizer.prepForAnnotation(news.getBody()));
            }
            JsonResponse response = Tools.formJsonResponse(indexedNewss, params.getQueryTimeStamp());
            logger.info(context.getRemoteAddr() + " -> " + "Returning stock news");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }

    @RequestMapping(path="/newsNER", method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStockNewsNamedEntities(IndexedStocksQueryParams params) {
        SolrQuery.SortClause clause = new SolrQuery.SortClause("articleDate", SolrQuery.ORDER.desc);
        try {
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, params.getQuery(), params.getQueryRows(), params.getQueryStart(), clause, params.getFilterQueries());
            Map<String, Map<String, Double>> allEntities = new HashMap<>();
            if (params.getEntityTypes().length > 0) {
                String entityType = params.getEntityTypes()[0];
                for (IndexedNews indexedNews : indexedNewss) {
                    String content = indexedNews.getBody();
                    Map<String, Double> entities = namedEntityRecognizer.detectNamedEntities(content, entityType, 0.5);
                    allEntities.put(indexedNews.getNERReportingForm(), entities);
                }
            }

            return ResponseEntity.ok().body(Tools.formJsonResponse(allEntities));
        } catch (SolrServerException e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }
}
