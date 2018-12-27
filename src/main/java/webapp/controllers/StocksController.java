package webapp.controllers;

import alphavantageapi.AlphavantageClient;
import alphavantageapi.model.StockPullOperation;
import common.Tools;
import nlp.NamedEntity;
import nlp.NamedEntityRecognizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.util.SimpleOrderedMap;
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
import webapp.services.NERModelTrainingService;
import webapp.services.RefreshStockDataService;
import webscraper.WebClient;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;
import java.util.*;

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
    private NERModelTrainingService nerModelTrainingService;

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

    @RequestMapping(path="/symbols", method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getSymbols() {
        logger.info(context.getRemoteAddr() + " -> " + "In getSymbols method");
        try {
            String[] symbols = getAvailableSymbols();
            return ResponseEntity.ok().body(Tools.formJsonResponse(symbols));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    private String[] getAvailableSymbols() throws SolrServerException {
        List<String> availableSymbols = new ArrayList<String>();
        SimpleOrderedMap<?> facets = solrClient.QueryFacets("body:*","{symbols:{type:terms,field:symbol,limit:10000}}");
        SimpleOrderedMap<?> symbols = (SimpleOrderedMap<?>) facets.get("symbols");
        List<?> buckets = (ArrayList<?>) symbols.get("buckets");
        for (int i = 0; i < buckets.size(); i++) {
            SimpleOrderedMap<?> nvpair = (SimpleOrderedMap<?>) buckets.get(i);
            String symbol = (String) nvpair.getVal(0);
            availableSymbols.add(symbol);
        }

        String[] symArr = availableSymbols.toArray(new String[availableSymbols.size()]);
        Arrays.sort(symArr);

        return symArr;
    }

    @RequestMapping(path="/news", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStockNews(IndexedStocksQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "In getStockNews method");
        try {
            params.setBody(new String[] {"*"});
            Random rand = new Random();
            SolrQuery.SortClause clause = new SolrQuery.SortClause("articleDate", "desc");
            //SolrQuery.SortClause clause = new SolrQuery.SortClause("articleDate", SolrQuery.ORDER.desc);
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, params.getQuery("articleDate"), params.getQueryRows(), params.getQueryStart(), clause, params.getFilterQueries());
            for (IndexedNews news : indexedNewss) {
                news.setParsed(namedEntityRecognizer.deepCleanText(news.getBody()));
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

    @RequestMapping(value="/news/{id}", method=RequestMethod.PUT, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> updateStockNews(@PathVariable(name="id") String id, @RequestPart("newsData") IndexedNews updIndexedNews) {
        try {
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, "id:" + id, 1, 0, null);
            if (!indexedNewss.isEmpty()) {
                IndexedNews indexedNews = indexedNewss.get(0);
                indexedNews.setAnnotated(updIndexedNews.getAnnotated());
                solrClient.indexDocument(indexedNews);
//                if (metadata.keySet().contains("annotated")) {
//                    nerModelTrainingService.process(this, (String)doc.get("category"));
//                }
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/trainNER", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> trainNERModel() {
        logger.info(context.getRemoteAddr() + " -> " + "In trainNERModel method");
        try {
            nerModelTrainingService.process(this);
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    public void initiateNERModelTraining() {
        namedEntityRecognizer.trainNERModel();
    }

    @RequestMapping(value="/annotate/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getAutoAnnotatedNews(@PathVariable(name="id") String id, int threshold) {
        logger.info(context.getRemoteAddr() + " -> " + "In autoAnnotate method");
        try {
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, "id:" + id, 1, 0, null);
            if (!indexedNewss.isEmpty()) {
                IndexedNews indexedNews = indexedNewss.get(0);

                double dblThreshold = (double)threshold / (double)100;
                String parsed = namedEntityRecognizer.deepCleanText(indexedNews.getBody());
                List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(parsed, dblThreshold);
                String annotated = namedEntityRecognizer.autoAnnotate(parsed, entities);

                return ResponseEntity.ok().body(Tools.formJsonResponse(annotated));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(path="/newsNER", method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStockNewsNamedEntities(IndexedStocksQueryParams params) {
        SolrQuery.SortClause clause = new SolrQuery.SortClause("articleDate", SolrQuery.ORDER.desc);
        try {
            List<IndexedNews> indexedNewss = solrClient.QueryIndexedDocuments(IndexedNews.class, params.getQuery("articleDate"), params.getQueryRows(), params.getQueryStart(), clause, params.getFilterQueries());
            Map<String, List<NamedEntity>> allEntities = new HashMap<>();
            if (params.getEntityTypes().length > 0) {
                String entityType = params.getEntityTypes()[0];
                for (IndexedNews indexedNews : indexedNewss) {
                    String parsed = namedEntityRecognizer.deepCleanText(indexedNews.getBody());
                    List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(parsed, 0.5);
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
